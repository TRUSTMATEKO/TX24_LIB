package kr.tx24.was.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.catalina.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Java WatchService를 사용하여 JDK 17+ 환경에서 효율적으로
 * 클래스 디렉토리 (하위 디렉토리 포함) 변경을 감지하고 Tomcat Context를 리로드하는 워처입니다.
 */
public class EmbeddedReloadWatcher {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddedReloadWatcher.class);
    private final Context context;
    private final Path classesPath;
    private final ExecutorService executor; 
    private WatchService watchService;
    // 하위 디렉토리의 WatchKey를 관리하기 위한 맵
    private final Map<WatchKey, Path> keys = new HashMap<>(); 
    // 리로드 요청 디바운싱을 위한 변수
    private volatile long lastReloadRequestTime = 0;
    private static final long RELOAD_DEBOUNCE_MILLIS = 3000; // 3초 내 중복 리로드 방지

    /**
     * @param context 리로드할 Tomcat Context
     * @param classesDir 감시할 컴파일된 클래스 파일 디렉토리 (예: target/classes)
     */
    public EmbeddedReloadWatcher(Context context, File classesDir) {
        this.context = context;
        this.classesPath = classesDir.toPath();
        this.executor = Executors.newSingleThreadExecutor(); 
    }

    /**
     * 파일 감시 서비스를 시작하고, 재귀적으로 모든 하위 디렉토리를 등록합니다.
     */
    public void start() {
        if (!Files.exists(classesPath) || !Files.isDirectory(classesPath)) {
            logger.warn("감시할 클래스 디렉토리({})가 존재하지 않거나 디렉토리가 아닙니다. 워처 시작 실패.", classesPath);
            return;
        }

        try {
            this.watchService = FileSystems.getDefault().newWatchService();
            // 재귀적으로 디렉토리 등록
            registerAll(classesPath);
            
            logger.info("EmbeddedReloadWatcher 시작. 감시 디렉토리: {}", classesPath);
            logger.info("총 {}개 디렉토리가 감시 등록되었습니다.", keys.size());
            
            executor.submit(this::watchLoop);
            Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
            
        } catch (IOException e) {
            logger.error("WatchService 초기화 또는 디렉토리 등록 중 오류 발생", e);
            this.stop();
        }
    }
    
    /**
     * 지정된 경로와 하위 디렉토리를 모두 WatchService에 등록합니다.
     */
    private void registerAll(final Path start) throws IOException {
        try (Stream<Path> stream = Files.walk(start)) {
            stream.filter(Files::isDirectory)
                  .forEach(this::registerDirectory);
        }
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
            // logger.debug("감시 디렉토리 등록 완료: {}", dir);
        } catch (IOException e) {
            logger.error("디렉토리 등록 실패: {}", dir, e);
        }
    }


    /**
     * WatchService에서 이벤트를 대기하고 처리하는 루프입니다.
     */
    private void watchLoop() {
        try {
            // Context.getState().isAvailable() 조건을 제거하여 WAS 시작 직후 종료되는 것을 방지합니다.
            // WAS 종료는 ShutdownHook과 WatchService.close()로만 제어합니다.
            while (!Thread.currentThread().isInterrupted()) { 
                WatchKey key;
                try {
                    // 키를 가져옵니다. (블로킹, 무기한 대기)
                    // Context.reload() 요청 시 WatchService가 잠시 멈출 수 있으므로, poll 대신 take()를 사용합니다.
                    key = watchService.take(); 
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // 인터럽트 상태 복원
                    break;
                }
                
                Path dir = keys.get(key);
                if (dir == null) {
                    continue;
                }

                // 리로드 요청 디바운싱 확인 및 시간 갱신
                long now = System.currentTimeMillis();
                if (now - lastReloadRequestTime < RELOAD_DEBOUNCE_MILLIS) {
                     key.reset();
                     logger.debug("리로드 요청 디바운싱: {}ms 내 중복 요청 무시.", RELOAD_DEBOUNCE_MILLIS);
                     continue;
                }
                
                // 이벤트 처리
                boolean shouldReload = false;
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    
                    if (kind == StandardWatchEventKinds.OVERFLOW) continue;
                    
                    @SuppressWarnings("unchecked")
                    Path name = ((WatchEvent<Path>) event).context();
                    Path child = dir.resolve(name); // 전체 경로

                    // 1. 클래스 파일 변경 감지
                    if (child.toString().endsWith(".class") || child.toString().endsWith(".jar")) {
                        logger.warn("클래스 파일 변경 감지! (유형: {}, 경로: {}).", 
                                    kind.name(), child.toString().replace(classesPath.toString(), ""));
                        shouldReload = true;
                    } 
                    
                    // 2. 새로운 디렉토리 처리 (재귀적 감시 유지)
                    if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                        registerDirectory(child);
                        // 새로운 디렉토리가 추가되었더라도 클래스 파일이 변경된 것은 아니므로, 리로드 요청은 하지 않습니다.
                    }
                }
                
                // 이벤트 처리 루프를 마친 후, 실제로 리로드를 요청합니다.
                if (shouldReload) {
                    lastReloadRequestTime = now; // 디바운싱 시간 갱신
                    logger.warn(">>> Tomcat Context 리로드 요청 시작: {}", context.getName());
                    
                    // 리로드 요청
                    context.reload();
                    
                    // 리로드 후, 잠시 대기하여 다음 키가 준비될 시간을 줍니다. (선택적)
                    try {
                         Thread.sleep(200); 
                    } catch (InterruptedException ignore) {
                         Thread.currentThread().interrupt();
                    }
                }

                // 키를 리셋하여 다음 이벤트를 받을 준비를 합니다.
                boolean valid = key.reset();
                if (!valid) {
                    logger.warn("WatchKey({})가 유효하지 않습니다. 감시 디렉토리가 삭제되었을 수 있습니다.", dir);
                    keys.remove(key); 
                    if (keys.isEmpty()) break;
                }
            }
        } catch (Exception e) {
            // Context가 정지되면서 발생할 수 있는 예외 포함
            logger.error("WatchService 감시 루프 종료됨 (예외 발생)", e);
        } finally {
            this.stop();
        }
    }

    /**
     * 워처 스레드와 WatchService를 안전하게 종료합니다.
     */
    public void stop() {
        try {
            if (watchService != null) {
                watchService.close();
            }
        } catch (IOException e) {
            logger.error("WatchService 종료 중 오류 발생", e);
        } finally {
            if (!executor.isShutdown()) {
                executor.shutdownNow();
                logger.info("EmbeddedReloadWatcher 종료됨.");
            }
        }
    }
}