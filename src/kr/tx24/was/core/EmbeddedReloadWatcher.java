package kr.tx24.was.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * @author juseop
 */
public class EmbeddedReloadWatcher {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddedReloadWatcher.class);
    
    private final Context context;
    private final Path classesPath;
    private final ExecutorService executor;
    private WatchService watchService;
    
    // 하위 디렉토리의 WatchKey를 관리하기 위한 맵
    private final Map<WatchKey, Path> keys = new HashMap<>();
    
    // 리로드 디바운싱 및 상태 관리
    private volatile long lastReloadRequestTime = 0;
    private static final long RELOAD_DEBOUNCE_MILLIS = 3000; // 3초
    
    // 리로드 후 쿨다운 시간
    private static final long RELOAD_COOLDOWN_MILLIS = 2000; // 2초
    
    // Watcher 상태 관리
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean isReloading = new AtomicBoolean(false);
    private final AtomicBoolean contextReadyLogged = new AtomicBoolean(false);
    
    // 재시도 설정
    private static final int MAX_RELOAD_RETRY = 3;
    
    /**
     * @param context 리로드할 Tomcat Context
     * @param classesDir 감시할 컴파일된 클래스 파일 디렉토리
     */
    public EmbeddedReloadWatcher(Context context, File classesDir) {
        this.context = context;
        this.classesPath = classesDir.toPath();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "EmbeddedReloadWatcher-Thread");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 파일 감시 서비스를 시작합니다.
     */
    public void start() {
        if (isRunning.get()) {
            return;
        }
        
        // 1. 디렉토리 존재 확인
        if (!Files.exists(classesPath) || !Files.isDirectory(classesPath)) {
            logger.error("감시할 클래스 디렉토리({})가 존재하지 않거나 디렉토리가 아닙니다.", classesPath);
            return;
        }

        try {
            // 2. WatchService 초기화
            this.watchService = FileSystems.getDefault().newWatchService();
            
            // 3. 재귀적으로 디렉토리 등록
            int dirCount = registerAll(classesPath);
            
            logger.info("EmbeddedReloadWatcher stated");
            logger.info("watch directory : {}, debounce : {}", classesPath,RELOAD_DEBOUNCE_MILLIS );
            
         
            isRunning.set(true);
            executor.submit(this::watchLoop);
            
            // 5. Shutdown Hook 등록
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                stop();
            }, "ShutdownHook-EmbeddedReloadWatcher"));
            
        } catch (IOException e) {
            logger.error("WatchService 초기화 또는 디렉토리 등록 중 오류 발생", e);
            stop();
        }
    }
    
    /**
     * 지정된 경로와 하위 디렉토리를 모두 WatchService에 등록합니다.
     * @return 등록된 디렉토리 수
     */
    private int registerAll(final Path start) throws IOException {
        int count = 0;
        try (Stream<Path> stream = Files.walk(start)) {
            for (Path path : stream.filter(Files::isDirectory).toList()) {
                registerDirectory(path);
                count++;
            }
        }
        return count;
    }
    
    /**
     * 단일 디렉토리를 WatchService에 등록하고 키를 맵에 저장합니다.
     */
    private void registerDirectory(Path dir) {
        try {
            WatchKey key = dir.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY
            );
            keys.put(key, dir);
        } catch (IOException e) {
            logger.error("directory watch fail : {}", dir, e);
        }
    }

    /**
     * WatchService에서 이벤트를 대기하고 처리하는 루프입니다.
     */
    private void watchLoop() {
        
        try {
            while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
                WatchKey key;
                
                try {
                    key = watchService.take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                
                checkContextReady();
                
                Path dir = keys.get(key);
                if (dir == null) {
                    key.reset();
                    continue;
                }

                // 이벤트 처리
                boolean shouldReload = processEvents(key, dir);
                
                // 리로드 실행
                if (shouldReload) {
                    performReload();
                }

                // 키 리셋
                boolean valid = key.reset();
                if (!valid) {
                    keys.remove(key);
                    if (keys.isEmpty()) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("watch exception : {}", e);
        } finally {
            stop();
        }
    }
    
    /**
     * Context가 준비되었는지 확인하고 최초 1회만 로깅합니다.
     */
    private void checkContextReady() {
        if (!contextReadyLogged.get()) {
            LifecycleState state = context.getState();
            if (state == LifecycleState.STARTED || state.isAvailable()) {
                contextReadyLogged.set(true);
            }
        }
    }
    
    /**
     * WatchKey의 이벤트를 처리하고 리로드 필요 여부를 반환합니다.
     */
    private boolean processEvents(WatchKey key, Path dir) {
        boolean shouldReload = false;
        
        for (WatchEvent<?> event : key.pollEvents()) {
            WatchEvent.Kind<?> kind = event.kind();
            
            if (kind == StandardWatchEventKinds.OVERFLOW) {
                logger.info("WatchEvent overflow 발생 (일부 이벤트 누락 가능)");
                continue;
            }
            
            @SuppressWarnings("unchecked")
            Path name = ((WatchEvent<Path>) event).context();
            Path child = dir.resolve(name);
            String fileName = name.toString();
            
            // 1. 클래스 파일 또는 JAR 파일 변경 감지
            if (fileName.endsWith(".class") || fileName.endsWith(".jar")) {
                String relativePath = classesPath.relativize(child).toString();
                logger.info("event : {},{}", kind.name(),relativePath);
                shouldReload = true;
            }
            
            // 2. 새로운 디렉토리 생성 시 재귀적 등록
            if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                    registerDirectory(child);
                    logger.debug("watch directory added : {}", child);
                }
            }
        }
        
        return shouldReload;
    }
    
    /**
     * Context 리로드를 실행합니다. (디바운싱 및 재시도 로직 포함)
     */
    private void performReload() {
        // 1. 디바운싱 체크
        long now = System.currentTimeMillis();
        if (now - lastReloadRequestTime < RELOAD_DEBOUNCE_MILLIS) {
            return;
        }
        
        // 2. 이미 리로드 중인지 체크
        if (!isReloading.compareAndSet(false, true)) {
            return;
        }
        
        try {
            lastReloadRequestTime = now;
            
            // 3. Context 상태 확인 (리로드 가능 여부 체크)
            LifecycleState currentState = context.getState();
            if (!isContextReadyForReload(currentState)) {
                return;
            }
            
            
            // 4. 리로드 실행 (재시도 로직)
            boolean success = reloadWithRetry();
            
            if (success) {
                logger.info("Tomcat context reloaded , Context : {} State :{}", context.getName(),currentState);
                
                // 5. 리로드 후 쿨다운 시간
                try {
                    Thread.sleep(RELOAD_COOLDOWN_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                logger.error("Tomcat context reload error (최대 재시도 횟수 초과)");
            }
            
        } finally {
            isReloading.set(false);
        }
    }
    
    /**
     * Context가 리로드 가능한 상태인지 확인합니다.
     */
    private boolean isContextReadyForReload(LifecycleState state) {
        // STARTED 또는 FAILED 상태에서만 리로드 가능
        return state == LifecycleState.STARTED || 
               state == LifecycleState.FAILED ||
               state.isAvailable();
    }
    
    /**
     * 재시도 로직을 포함한 리로드 실행
     */
    private boolean reloadWithRetry() {
        for (int attempt = 1; attempt <= MAX_RELOAD_RETRY; attempt++) {
            try {
                
                // Context 상태 확인
                LifecycleState stateBefore = context.getState();
                
                // 리로드 실행
                context.reload();
                
                // 리로드 완료 대기
                Thread.sleep(1000);
                
                // Context 상태 확인
                LifecycleState stateAfter = context.getState();
                
                // 성공 확인
                if (stateAfter == LifecycleState.STARTED || stateAfter.isAvailable()) {
                    return true;
                } else {
                    
                    if (attempt < MAX_RELOAD_RETRY) {
                        logger.info("  재시도 대기 중... ({}초)", attempt);
                        Thread.sleep(attempt * 1000L);
                    }
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Tomcat reloader interrupted ", e);
                return false;
            } catch (Exception e) {
                logger.error("Tomcat reload count :{} failure: {}", attempt, e.getMessage(), e);
                if (attempt < MAX_RELOAD_RETRY) {
                    try {
                        Thread.sleep(attempt * 1000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
        }
        
        return false;
    }

    /**
     * 워처 스레드와 WatchService를 안전하게 종료합니다.
     */
    public void stop() {
        if (!isRunning.compareAndSet(true, false)) {
            return;
        }
        
        
        // 1. WatchService 종료
        try {
            if (watchService != null) {
                watchService.close();
            }
        } catch (IOException e) {
            //logger.error("WatchService 종료 중 오류 발생", e);
        }
        
        // 2. Executor 종료
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                } else {
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // 3. 리소스 정리
        keys.clear();
    }
    
    /**
     * 현재 Watcher 상태 확인
     */
    public boolean isRunning() {
        return isRunning.get();
    }
    
    /**
     * 현재 리로드 진행 중 여부 확인
     */
    public boolean isReloading() {
        return isReloading.get();
    }
}