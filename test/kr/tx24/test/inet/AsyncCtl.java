package kr.tx24.test.inet;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;
import kr.tx24.inet.mapper.Autowired;
import kr.tx24.inet.mapper.Controller;
import kr.tx24.inet.mapper.Data;
import kr.tx24.inet.mapper.Route;
import kr.tx24.inet.util.INetRespUtils;
import kr.tx24.lib.executor.AsyncExecutor;
import kr.tx24.lib.map.LinkedMap;

@Controller(target = "/async")
public class AsyncCtl {
	private static final Logger logger = LoggerFactory.getLogger(AsyncCtl.class);
	
	
	@Autowired
    private ChannelHandlerContext ctx;
    
    
    // ========== 기본 비동기 응답 ==========
    
    /**
     * 예시 1: 기본 비동기 응답
     * 응답을 비동기로 전송하고 결과를 처리
     */
    @Route(target = "/basic")
    public void basicAsync() {
        logger.info("Starting basic async response");
        
        CompletableFuture<Void> future = INetRespUtils.success(ctx)
            .message("비동기 응답 전송")
            .data("timestamp", System.currentTimeMillis())
            .data("method", "basicAsync")
            .sendAsync();
        
        // 응답 전송 완료 후 처리
        future.thenRun(() -> {
            logger.info("Basic async response sent successfully");
        }).exceptionally(throwable -> {
            logger.error("Failed to send basic async response", throwable);
            return null;
        });
    }
    
    /**
     * 예시 2: 즉시 응답 후 백그라운드 작업
     * 클라이언트에게 즉시 응답하고, 무거운 작업은 백그라운드에서 수행
     */
    @Route(target = "/immediate")
    public void immediateResponseWithBackgroundWork(@Data LinkedMap<String, Object> data) {
        String taskId = "TASK-" + System.currentTimeMillis();
        
        logger.info("Sending immediate response for taskId: {}", taskId);
        
        // 즉시 응답 전송
        CompletableFuture<Void> responseFuture = INetRespUtils.success(ctx)
            .message("작업이 접수되었습니다")
            .data("taskId", taskId)
            .data("status", "ACCEPTED")
            .data("message", "백그라운드에서 처리 중입니다")
            .sendAsync();
        
        // 응답 전송 후 무거운 작업 시작
        responseFuture.thenRunAsync(() -> {
            logger.info("Starting background work for taskId: {}", taskId);
            
            try {
                // 무거운 작업 시뮬레이션 (5초)
                Thread.sleep(5000);
                
                // 작업 완료 로깅
                logger.info("✓ Background work completed for taskId: {}", taskId);
                
                // 결과를 DB나 캐시에 저장
                saveTaskResult(taskId, "COMPLETED");
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("✗ Background work interrupted for taskId: {}", taskId);
            } catch (Exception e) {
                logger.error("✗ Background work failed for taskId: {}", taskId, e);
                saveTaskResult(taskId, "FAILED");
            }
        }, AsyncExecutor.getExecutor());
    }
    
    /**
     * 예시 3: 비동기 체인 작업
     * 여러 비동기 작업을 순차적으로 체인
     */
    @Route(target = "/chain")
    public void asyncChain(@Data LinkedMap<String, Object> requestData) {
        String userId = requestData.getString("userId");
        
        logger.info("Starting async chain for userId: {}", userId);
        
        // 1단계: 즉시 응답
        CompletableFuture<Void> step1 = INetRespUtils.success(ctx)
            .message("처리 시작")
            .data("step", 1)
            .data("userId", userId)
            .sendAsync();
        
        // 2단계: 사용자 정보 조회 (비동기)
        step1.thenComposeAsync(v -> 
            CompletableFuture.supplyAsync(() -> {
                logger.info("Step 2: Fetching user info for {}", userId);
                try {
                    Thread.sleep(1000); // API 호출 시뮬레이션
                    return fetchUserInfo(userId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("User fetch interrupted", e);
                }
            }, AsyncExecutor.getExecutor())
        )
        // 3단계: 권한 확인 (비동기)
        .thenComposeAsync(userInfo -> 
            CompletableFuture.supplyAsync(() -> {
                logger.info("Step 3: Checking permissions for {}", userId);
                try {
                    Thread.sleep(500); // 권한 확인 시뮬레이션
                    return checkPermissions(userInfo);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Permission check interrupted", e);
                }
            }, AsyncExecutor.getExecutor())
        )
        // 4단계: 최종 처리
        .thenAcceptAsync(hasPermission -> {
            logger.info("Step 4: Final processing - hasPermission: {}", hasPermission);
            // 최종 결과를 DB나 캐시에 저장
            saveProcessingResult(userId, hasPermission);
        }, AsyncExecutor.getExecutor())
        // 에러 처리
        .exceptionally(throwable -> {
            logger.error("✗ Async chain failed for userId: {}", userId, throwable);
            return null;
        });
    }
    
    /**
     * 예시 4: 병렬 비동기 작업
     * 여러 작업을 동시에 수행하고 모두 완료될 때까지 대기
     */
    @Route(target = "/parallel")
    public void parallelAsync(@Data LinkedMap<String, Object> requestData) {
        String orderId = requestData.getString("orderId");
        
        logger.info("Starting parallel async operations for orderId: {}", orderId);
        
        // 즉시 응답 전송
        CompletableFuture<Void> responseFuture = INetRespUtils.success(ctx)
            .message("주문 처리 시작")
            .data("orderId", orderId)
            .data("status", "PROCESSING")
            .sendAsync();
        
        // 응답 후 여러 작업을 병렬로 수행
        responseFuture.thenRunAsync(() -> {
            // 3개의 독립적인 작업을 병렬로 수행
            CompletableFuture<String> task1 = CompletableFuture.supplyAsync(() -> {
                logger.info("Task 1: Checking inventory");
                sleep(2000);
                return "Inventory: OK";
            }, AsyncExecutor.getExecutor());
            
            CompletableFuture<String> task2 = CompletableFuture.supplyAsync(() -> {
                logger.info("Task 2: Processing payment");
                sleep(3000);
                return "Payment: SUCCESS";
            }, AsyncExecutor.getExecutor());
            
            CompletableFuture<String> task3 = CompletableFuture.supplyAsync(() -> {
                logger.info("Task 3: Sending notification");
                sleep(1000);
                return "Notification: SENT";
            }, AsyncExecutor.getExecutor());
            
            // 모든 작업이 완료될 때까지 대기
            CompletableFuture.allOf(task1, task2, task3)
                .thenRun(() -> {
                    try {
                        logger.info("✓ All parallel tasks completed:");
                        logger.info("  - {}", task1.get());
                        logger.info("  - {}", task2.get());
                        logger.info("  - {}", task3.get());
                        
                        // 최종 결과 저장
                        saveOrderResult(orderId, "COMPLETED");
                        
                    } catch (Exception e) {
                        logger.error("✗ Failed to get task results", e);
                    }
                })
                .exceptionally(throwable -> {
                    logger.error("✗ Parallel tasks failed for orderId: {}", orderId, throwable);
                    saveOrderResult(orderId, "FAILED");
                    return null;
                });
        }, AsyncExecutor.getExecutor());
    }
    
    /**
     * 예시 5: 타임아웃이 있는 비동기 응답
     * 일정 시간 내에 완료되지 않으면 타임아웃 처리
     */
    @Route(target = "/timeout")
    public void asyncWithTimeout(@Data LinkedMap<String, Object> requestData) {
        String requestId = requestData.getString("requestId");
        
        logger.info("Starting async operation with timeout for requestId: {}", requestId);
        
        // 즉시 응답
        CompletableFuture<Void> responseFuture = INetRespUtils.success(ctx)
            .message("처리 시작 (타임아웃: 3초)")
            .data("requestId", requestId)
            .sendAsync();
        
        // 응답 후 타임아웃이 있는 작업 수행
        responseFuture.thenRunAsync(() -> {
            CompletableFuture<String> workFuture = CompletableFuture.supplyAsync(() -> {
                logger.info("Performing work with timeout...");
                sleep(5000); // 5초 걸리는 작업 (타임아웃보다 김)
                return "Work completed";
            }, AsyncExecutor.getExecutor());
            
            try {
                // 3초 타임아웃 설정
                String result = workFuture.get(3, TimeUnit.SECONDS);
                logger.info("✓ Work completed within timeout: {}", result);
                
            } catch (TimeoutException e) {
                logger.warn("⚠ Work timed out for requestId: {}", requestId);
                workFuture.cancel(true); // 작업 취소
                saveWorkResult(requestId, "TIMEOUT");
                
            } catch (Exception e) {
                logger.error("✗ Work failed for requestId: {}", requestId, e);
                saveWorkResult(requestId, "FAILED");
            }
        }, AsyncExecutor.getExecutor());
    }
    
    /**
     * 예시 6: 조건부 비동기 응답
     * 특정 조건에 따라 다른 비동기 처리
     */
    @Route(target = "/conditional")
    public void conditionalAsync(@Data LinkedMap<String, Object> requestData) {
        String type = requestData.getString("type");
        String dataId = requestData.getString("dataId");
        
        logger.info("Starting conditional async for type: {}, dataId: {}", type, dataId);
        
        CompletableFuture<Void> future;
        
        if ("priority".equals(type)) {
            // 우선순위 요청: 즉시 처리
            future = INetRespUtils.success(ctx)
                .message("우선순위 요청 - 즉시 처리")
                .data("type", "priority")
                .data("dataId", dataId)
                .sendAsync();
            
            future.thenRunAsync(() -> {
                logger.info("Processing priority request immediately");
                processPriorityRequest(dataId);
            }, AsyncExecutor.getExecutor());
            
        } else {
            // 일반 요청: 큐에 추가
            future = INetRespUtils.success(ctx)
                .message("일반 요청 - 큐에 추가됨")
                .data("type", "normal")
                .data("dataId", dataId)
                .data("estimatedTime", "5-10분")
                .sendAsync();
            
            future.thenRunAsync(() -> {
                logger.info("Adding normal request to queue");
                addToQueue(dataId);
            }, AsyncExecutor.getExecutor());
        }
        
        // 공통 후처리
        future.thenRun(() -> {
            logger.info("✓ Conditional async completed for dataId: {}", dataId);
        }).exceptionally(throwable -> {
            logger.error("✗ Conditional async failed for dataId: {}", dataId, throwable);
            return null;
        });
    }
    
    /**
     * 예시 7: 재시도 로직이 있는 비동기 응답
     * 실패 시 자동으로 재시도
     */
    @Route(target = "/retry")
    public void asyncWithRetry(@Data LinkedMap<String, Object> requestData) {
        String taskId = requestData.getString("taskId");
        int maxRetries = 3;
        
        logger.info("Starting async with retry for taskId: {}", taskId);
        
        // 즉시 응답
        CompletableFuture<Void> responseFuture = INetRespUtils.success(ctx)
            .message("작업 시작 (최대 재시도: " + maxRetries + "회)")
            .data("taskId", taskId)
            .sendAsync();
        
        // 응답 후 재시도 로직 수행
        responseFuture.thenRunAsync(() -> {
            executeWithRetry(taskId, maxRetries);
        }, AsyncExecutor.getExecutor());
    }
    
    /**
     * 예시 8: 여러 클라이언트에게 동시 응답 (브로드캐스트 패턴)
     * 하나의 요청으로 여러 응답 전송
     */
    @Route(target = "/broadcast")
    public void asyncBroadcast(@Data LinkedMap<String, Object> requestData) {
        String message = requestData.getString("message");
        
        logger.info("Broadcasting message: {}", message);
        
        // 첫 번째 응답: 즉시 확인
        CompletableFuture<Void> ack = INetRespUtils.success(ctx)
            .message("브로드캐스트 시작")
            .data("message", message)
            .data("status", "BROADCASTING")
            .sendAsync();
        
        // 두 번째 응답: 진행 상황 (연결 유지 필요)
        ack.thenRunAsync(() -> {
            sleep(2000);
            
            // 주의: 동일한 ctx로 여러 번 응답하려면 autoClose(false) 필요
            // 하지만 현재 구조에서는 연결이 이미 닫혔을 수 있음
            logger.info("✓ Broadcast progress: 50%");
            
        }, AsyncExecutor.getExecutor())
        .thenRunAsync(() -> {
            sleep(2000);
            logger.info("✓ Broadcast completed: 100%");
        }, AsyncExecutor.getExecutor())
        .exceptionally(throwable -> {
            logger.error("✗ Broadcast failed", throwable);
            return null;
        });
    }
    
    /**
     * 예시 9: 결과를 콜백으로 전달하는 비동기 응답
     */
    @Route(target = "/callback")
    public void asyncWithCallback(@Data LinkedMap<String, Object> requestData) {
        String callbackUrl = requestData.getString("callbackUrl");
        String jobId = "JOB-" + System.currentTimeMillis();
        
        logger.info("Starting async job with callback. jobId: {}, callbackUrl: {}", 
                   jobId, callbackUrl);
        
        // 즉시 응답
        CompletableFuture<Void> future = INetRespUtils.success(ctx)
            .message("작업이 시작되었습니다")
            .data("jobId", jobId)
            .data("callbackUrl", callbackUrl)
            .data("status", "STARTED")
            .sendAsync();
        
        // 작업 수행 후 콜백
        future.thenComposeAsync(v -> 
            CompletableFuture.supplyAsync(() -> {
                logger.info("Performing long-running job: {}", jobId);
                sleep(3000);
                return processJob(jobId);
            }, AsyncExecutor.getExecutor())
        )
        .thenAcceptAsync(result -> {
            logger.info("Job completed: {}, sending callback to: {}", jobId, callbackUrl);
            sendCallback(callbackUrl, jobId, result);
        }, AsyncExecutor.getExecutor())
        .exceptionally(throwable -> {
            logger.error("✗ Job failed: {}", jobId, throwable);
            sendCallback(callbackUrl, jobId, "FAILED: " + throwable.getMessage());
            return null;
        });
    }
    
    // ========== Helper Methods ==========
    
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private void saveTaskResult(String taskId, String status) {
        logger.info("Saving task result: {} - {}", taskId, status);
        // DB 저장 로직
    }
    
    private Map<String, Object> fetchUserInfo(String userId) {
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("userId", userId);
        userInfo.put("name", "User " + userId);
        userInfo.put("role", "ADMIN");
        return userInfo;
    }
    
    private boolean checkPermissions(Map<String, Object> userInfo) {
        String role = (String) userInfo.get("role");
        return "ADMIN".equals(role);
    }
    
    private void saveProcessingResult(String userId, boolean result) {
        logger.info("Processing result for {}: {}", userId, result);
    }
    
    private void saveOrderResult(String orderId, String status) {
        logger.info("Order result: {} - {}", orderId, status);
    }
    
    private void saveWorkResult(String requestId, String status) {
        logger.info("Work result: {} - {}", requestId, status);
    }
    
    private void processPriorityRequest(String dataId) {
        logger.info("Processing priority request: {}", dataId);
        sleep(1000);
    }
    
    private void addToQueue(String dataId) {
        logger.info("Adding to queue: {}", dataId);
    }
    
    private void executeWithRetry(String taskId, int maxRetries) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                logger.info("Attempt {}/{} for taskId: {}", attempt, maxRetries, taskId);
                
                // 작업 수행 (실패 시뮬레이션)
                if (Math.random() < 0.7) { // 70% 실패율
                    throw new RuntimeException("Simulated failure");
                }
                
                logger.info("✓ Task succeeded on attempt {}", attempt);
                saveTaskResult(taskId, "SUCCESS");
                return;
                
            } catch (Exception e) {
                logger.warn("⚠ Task failed on attempt {}: {}", attempt, e.getMessage());
                
                if (attempt == maxRetries) {
                    logger.error("✗ Task failed after {} attempts", maxRetries);
                    saveTaskResult(taskId, "FAILED");
                } else {
                    sleep(1000 * attempt); // 점진적 백오프
                }
            }
        }
    }
    
    private String processJob(String jobId) {
        return "Job " + jobId + " completed successfully";
    }
    
    private void sendCallback(String callbackUrl, String jobId, String result) {
        logger.info("Sending callback to {}: jobId={}, result={}", callbackUrl, jobId, result);
        // HTTP 콜백 전송 로직
    }
    
    // ========== Cleanup ==========
    
    public static void shutdown() {
        logger.info("Shutting down executor service...");
        AsyncExecutor.getExecutor().shutdown();
        try {
            if (!AsyncExecutor.getExecutor().awaitTermination(10, TimeUnit.SECONDS)) {
            	AsyncExecutor.getExecutor().shutdownNow();
            }
        } catch (InterruptedException e) {
        	AsyncExecutor.getExecutor().shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
