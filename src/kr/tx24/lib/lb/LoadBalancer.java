package kr.tx24.lib.lb;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
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

import com.fasterxml.jackson.core.type.TypeReference;

import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.mapper.JacksonUtils;

public class LoadBalancer {

    private static final Logger logger = LoggerFactory.getLogger(LoadBalancer.class);

    private static final Path LB_CONF = SystemUtils.getLoadBalanceConfigPath();
    private static ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> SERVER_POOLS;
    private static ConcurrentHashMap<String, List<String>> SERVER_LIST;
    private static ConcurrentHashMap<String, AtomicInteger> SERVER_POSITION;
    private static ConcurrentHashMap<String, Set<String>> SERVER_BROKEN_LIST;
    private static volatile boolean enabled;
    private static volatile boolean started;
    private static volatile long lastConfigModified;
    
    private static final int DEFAULT_INTERVAL = 10;
    private static ScheduledExecutorService scheduler;
    private static final Object START_LOCK = new Object();

    public static boolean isEnabled() {
        return enabled;
    }

    public static void start() {
        start(DEFAULT_INTERVAL);
    }
    
    

    public static void start(int intervalSeconds) {
        synchronized (START_LOCK) {
            if (started) {
                logger.warn("LoadBalancer is already started. Ignoring duplicate start() call.");
                return;
            }
            
            
            SERVER_POOLS = new ConcurrentHashMap<>();
            SERVER_LIST = new ConcurrentHashMap<>();
            SERVER_POSITION = new ConcurrentHashMap<>();
            SERVER_BROKEN_LIST = new ConcurrentHashMap<>();
            enabled = false;
            started = false;
            lastConfigModified = 0;
            scheduler = Executors.newSingleThreadScheduledExecutor();
            
            File configFile = LB_CONF.toFile();
            if(!Files.exists(LB_CONF)) {
                logger.error("LoadBalancer config not found: {}", LB_CONF);
                return;
            }

            logger.info("LoadBalancer config: {}", LB_CONF);
            
            // 즉시 초기 설정 로드   
            reloadConfigIfModified(configFile);
            if (!enabled) {
            	System.err.println("NLB 활성화 실패로 자동 종료합니다. 자동 시작을 없애려면 -DNLB 또는 nlb.json 파일을 삭제하시기 바랍니다.");
            	System.exit(1);
            }
        
            
            logger.info("LoadBalancer started, interval {} sec", intervalSeconds);

            scheduler.scheduleAtFixedRate(() -> {
                try {
                    reloadBrokenServers();
                    reloadConfigIfModified(configFile);
                } catch (Exception e) {
                    logger.error("LoadBalancer scheduler error", e);
                }
            }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
            
            
            
            started = true;
        }
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

            
            Map<String, Map<String, Integer>> pools = 
            		new JacksonUtils().fromJson(Files.readAllBytes(configFile.toPath()), new TypeReference<Map<String, Map<String, Integer>>>() {});
            
            pools.forEach((key, serverMap) -> {
                List<String> weightedList = serverMap.entrySet().stream()
                        .flatMap(e -> Collections.nCopies(Math.min(e.getValue(), 100), e.getKey()).stream())
                        .collect(Collectors.toList());
                Collections.shuffle(weightedList);

                SERVER_LIST.put(key, weightedList);
                SERVER_POSITION.putIfAbsent(key, new AtomicInteger(0));
            });

            pools.forEach((key, serverMap) -> SERVER_POOLS.put(key, new ConcurrentHashMap<>(serverMap)));
            enabled = true;
            lastConfigModified = modified;
            logger.info("LoadBalancer config loaded:\n{}", new JacksonUtils().toJson(SERVER_LIST));

        } catch (Exception e) {
            logger.warn("LoadBalancer config reload failed: {}", e.getMessage());
        }
    }

    private static boolean isAlive(String server) {
        String[] parts = server.split(":");
        if (parts.length != 2) return false;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(parts[0], Integer.parseInt(parts[1])), 500);
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
    
    
    
    public static synchronized void shutdown() {
    	if(scheduler != null) {
    	
	        if (scheduler.isShutdown()) {
	            return;
	        }
	        
	        //logger.info("LoadBalancer shutting down...");
	        
	        try {
	            //즉시 강제 종료
	            scheduler.shutdownNow();
	            
	            // 종료 확인만 (1초)
	            if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
	                logger.warn("LoadBalancer scheduler could not be terminated within 1 second");
	            } else {
	                logger.info("LoadBalancer scheduler shutdown completed");
	            }
	            
	            started = false;
	            enabled = false;
	            
	        } catch (InterruptedException e) {
	            logger.warn("LoadBalancer shutdown interrupted");
	            Thread.currentThread().interrupt();
	        }
    	}
    }
}