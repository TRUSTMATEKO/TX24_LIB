package kr.tx24.lib.lb;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.mapper.JacksonUtils;

public class LoadBalancer {

    private static final Logger logger = LoggerFactory.getLogger(LoadBalancer.class);

    private static final String LB_CONF = SystemUtils.getLoadBalanceConfig();
    private static final ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> SERVER_POOLS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, List<String>> SERVER_LIST = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicInteger> SERVER_POSITION = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Set<String>> SERVER_BROKEN_LIST = new ConcurrentHashMap<>();
    private static volatile boolean enabled = false;
    private static volatile long lastConfigModified = 0;
    
    
    private static final int DEFAULT_INTERVAL = 5;
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public static boolean isEnabled() {
        return enabled;
    }

    public static void start() {
        start(DEFAULT_INTERVAL);
    }
    
    

    public static void start(int intervalSeconds) {
        File configFile = new File(LB_CONF);
        if (!configFile.exists()) {
            logger.error("LoadBalancer config not found: {}", LB_CONF);
            return;
        }

        logger.info("LoadBalancer config: {}", LB_CONF);
        logger.info("LoadBalancer started, interval {} sec", intervalSeconds);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                reloadBrokenServers();
                reloadConfigIfModified(configFile);
            } catch (Exception e) {
                logger.error("LoadBalancer scheduler error", e);
            }
        }, 0, intervalSeconds, TimeUnit.SECONDS);
    }

    private static void reloadBrokenServers() {
        SERVER_BROKEN_LIST.forEach((key, brokenSet) -> {
            brokenSet.removeIf(server -> {
                boolean alive = isAlive(server);
                if (alive) logger.info("Remove broken server {}: alive again", server);
                return alive;
            });
        });
    }

    private static void reloadConfigIfModified(File configFile) {
        try {
            long modified = configFile.lastModified();
            if (modified == 0 || modified == lastConfigModified) {
                return; // 변경 없음 → 리턴
            }

            byte[] bytes = Files.readAllBytes(configFile.toPath());
            ObjectMapper mapper = new ObjectMapper();
            mapper.setDefaultPropertyInclusion(Include.NON_NULL);
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            Map<String, Map<String, Integer>> pools = mapper.readValue(
                    bytes,
                    new TypeReference<Map<String, Map<String, Integer>>>() {}
            );

            pools.forEach((key, serverMap) -> {
                List<String> weightedList = serverMap.entrySet().stream()
                        .flatMap(e -> Collections.nCopies(Math.min(e.getValue(), 5), e.getKey()).stream())
                        .collect(Collectors.toList());
                Collections.shuffle(weightedList);

                SERVER_LIST.put(key, weightedList);
                SERVER_POSITION.putIfAbsent(key, new AtomicInteger(0));
            });

            pools.forEach((key, serverMap) -> SERVER_POOLS.put(key, new ConcurrentHashMap<>(serverMap)));
            enabled = true;
            lastConfigModified = modified;
            logger.info("LoadBalancer config reloaded:\n{}", new JacksonUtils().toJson(SERVER_LIST));

        } catch (Exception e) {
            logger.warn("LoadBalancer config reload failed: {}", e.getMessage());
        }
    }

    private static boolean isAlive(String server) {
        String[] parts = server.split(":");
        if (parts.length != 2) return false;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(parts[0], Integer.parseInt(parts[1])), 300);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static Map<String, List<String>> getServerList() {
        return Collections.unmodifiableMap(SERVER_LIST);
    }

    public static Map<String, Set<String>> getBrokenServerList() {
        return Collections.unmodifiableMap(SERVER_BROKEN_LIST);
    }

    public static String getServer(String key) {
        List<String> servers = SERVER_LIST.get(key);
        if (servers == null || servers.isEmpty()) return "UNKNOWN_SERVER:PORT";

        AtomicInteger pos = SERVER_POSITION.computeIfAbsent(key, k -> new AtomicInteger(0));
        int index = pos.getAndUpdate(i -> (i + 1) % servers.size());
        return servers.get(index);
    }

    public static String getExcludeBrokenServer(String key) {
        Set<String> broken = SERVER_BROKEN_LIST.getOrDefault(key, Collections.emptySet());
        List<String> servers = SERVER_LIST.get(key);
        if (servers == null || servers.isEmpty()) return "UNKNOWN_SERVER:PORT";

        for (int i = 0; i < servers.size(); i++) {
            String server = getServer(key);
            if (!broken.contains(server)) return server;
        }
        return servers.get(0); // fallback
    }

    public static void setBrokenServer(String key, String server) {
        SERVER_BROKEN_LIST.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(server);
        logger.info("Added broken server: {}", server);
    }
}
