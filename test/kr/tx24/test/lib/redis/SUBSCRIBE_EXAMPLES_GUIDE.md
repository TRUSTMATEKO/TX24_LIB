# RedisPubSub Subscribe 실전 예제 가이드 (순수 Java)

## 📦 생성된 파일 목록

### 1. 핵심 라이브러리
- **RedisPubSub.java** - Pub/Sub 전용 Connection 관리
- **RedisJsonCodec.java** - JSON 직렬화/역직렬화

### 2. 실전 예제 (바로 실행 가능)

#### 🎯 [RedisPubSubSimpleExample.java](computer:///mnt/user-data/outputs/RedisPubSubSimpleExample.java) (12KB)
**가장 기본적인 예제 - 메인 메서드로 바로 실행**

4가지 시나리오:
1. 단순 메시지 수신
2. **작업 클래스 자동 실행** ⭐
3. 배치 작업 처리
4. 실시간 알림 시스템

```java
// 실행 방법
public static void main(String[] args) {
    // DataProcessor, FileHandler 등 특정 클래스가
    // Redis 메시지를 받아 자동으로 실행됨
}
```

---

#### 🏭 [RedisPubSubWorker.java](computer:///mnt/user-data/outputs/RedisPubSubWorker.java) (4.6KB)
**Worker 패턴 - 분산 작업 처리**

여러 Worker가 동일한 채널을 구독하여 작업을 분산 처리:
- 3개의 Worker가 10개의 작업을 나눠서 처리
- 작업 통계 수집
- Load Balancing

```java
// Worker 3개가 jobs:images 채널을 구독
RedisPubSubWorker worker1 = new RedisPubSubWorker("WORKER-1", "jobs:images", imageProcessor);
RedisPubSubWorker worker2 = new RedisPubSubWorker("WORKER-2", "jobs:images", imageProcessor);
RedisPubSubWorker worker3 = new RedisPubSubWorker("WORKER-3", "jobs:images", imageProcessor);

worker1.start();
worker2.start();
worker3.start();

// 작업 발행 → Worker들이 자동으로 분산 처리
for (int i = 1; i <= 10; i++) {
    RedisPubSub.publish("jobs:images", "image_" + i + ".jpg");
}
```

---

#### 🎪 [RedisPubSubEventBus.java](computer:///mnt/user-data/outputs/RedisPubSubEventBus.java) (12KB)
**이벤트 버스 패턴 - 느슨한 결합**

하나의 이벤트 발생 시 여러 Listener가 자동 실행:

```java
RedisPubSubEventBus eventBus = new RedisPubSubEventBus();

// 주문 생성 시 4개의 Listener가 자동 실행
eventBus.addEventListener("order.created", new InventoryListener());   // 재고 감소
eventBus.addEventListener("order.created", new EmailListener());       // 이메일 발송
eventBus.addEventListener("order.created", new PointListener());       // 포인트 적립
eventBus.addEventListener("order.created", new AnalyticsListener());   // 통계 업데이트

// 주문 생성 이벤트 발행 → 4개 Listener가 모두 실행됨
RedisPubSubEventBus.publishEvent("order.created", Map.of(
    "orderId", "ORD-12345",
    "customerId", "CUST-1001",
    "amount", 50000
));
```

---

## 🚀 빠른 시작

### 1️⃣ 가장 간단한 예제부터 시작

```bash
# RedisPubSubSimpleExample 실행
java RedisPubSubSimpleExample
```

**메뉴 선택:**
```
1. 단순 메시지 수신
2. 작업 클래스 자동 실행  ⭐ 추천
3. 배치 작업 처리
4. 실시간 알림 시스템
5. 모두 실행
```

---

### 2️⃣ 예제 2 선택 시 실행 흐름

```
[사용자] → 2번 선택

[시스템] → 채널 'task:execute' 구독 시작
        → DataProcessor, FileHandler 클래스 준비

[시스템] → 메시지 발행: "PROCESS:user_data_2025"
        → DataProcessor.process() 자동 실행 ✅

[시스템] → 메시지 발행: "SAVE:report.pdf"
        → FileHandler.save() 자동 실행 ✅

[시스템] → 메시지 발행: "DELETE:temp.log"
        → FileHandler.delete() 자동 실행 ✅
```

---

## 📖 핵심 코드 패턴

### 패턴 1: 간단한 메시지 수신 후 클래스 실행

```java
// 1. 실행할 클래스 준비
DataProcessor processor = new DataProcessor();

// 2. Subscribe 시작 - 메시지 수신 시 클래스 메서드 실행
RedisPubSub.Subscriber subscriber = RedisPubSub.subscribe("my-channel", message -> {
    processor.process(message);  // ✅ 클래스 메서드 자동 실행
});

// 3. 메시지 발행 (다른 시스템에서)
RedisPubSub.publish("my-channel", "some-data");

// 4. 종료 시
subscriber.close();
```

---

### 패턴 2: 메시지 타입에 따라 다른 클래스 실행

```java
// 여러 작업 클래스들
EmailService emailService = new EmailService();
ReportService reportService = new ReportService();
CacheService cacheService = new CacheService();

// Subscribe - 메시지 파싱하여 해당 클래스 실행
RedisPubSub.Subscriber subscriber = RedisPubSub.subscribe("tasks", message -> {
    
    if (message.startsWith("EMAIL:")) {
        String email = message.substring(6);
        emailService.send(email);           // ✅ EmailService 실행
        
    } else if (message.startsWith("REPORT:")) {
        String type = message.substring(7);
        reportService.generate(type);       // ✅ ReportService 실행
        
    } else if (message.startsWith("CACHE:")) {
        String key = message.substring(6);
        cacheService.clear(key);            // ✅ CacheService 실행
    }
});
```

---

### 패턴 3: JSON 메시지로 복잡한 데이터 전달

```java
// 작업 요청 클래스
class TaskRequest {
    String taskType;
    Map<String, Object> params;
}

// TaskExecutor 클래스
TaskExecutor executor = new TaskExecutor();

// JSON Subscribe
RedisPubSub.Subscriber subscriber = RedisPubSub.subscribeJson(
    "tasks", 
    TaskRequest.class, 
    request -> {
        executor.execute(request.taskType, request.params);  // ✅ 실행
    }
);

// 발행 (다른 시스템)
TaskRequest request = new TaskRequest("SEND_EMAIL", Map.of("to", "user@example.com"));
RedisPubSub.publishJson("tasks", request);
```

---

## 🎓 실전 활용 시나리오

### 시나리오 1: 이미지 업로드 후 처리

```java
// ImageProcessor 클래스
class ImageProcessor {
    public void createThumbnail(String filename) { /* 썸네일 생성 */ }
    public void scanVirus(String filename) { /* 바이러스 검사 */ }
    public void backup(String filename) { /* 백업 */ }
}

ImageProcessor processor = new ImageProcessor();

// 이미지 업로드 이벤트 구독
RedisPubSub.Subscriber subscriber = RedisPubSub.subscribe("image.uploaded", filename -> {
    processor.createThumbnail(filename);  // ✅ 자동 실행
    processor.scanVirus(filename);        // ✅ 자동 실행
    processor.backup(filename);           // ✅ 자동 실행
});

// 파일 업로드 완료 시 (다른 시스템에서)
RedisPubSub.publish("image.uploaded", "photo_2025.jpg");
```

---

### 시나리오 2: 주문 처리 파이프라인

```java
// 주문 처리 클래스들
OrderService orderService = new OrderService();
PaymentService paymentService = new PaymentService();
InventoryService inventoryService = new InventoryService();
ShippingService shippingService = new ShippingService();

// 각 단계별 Subscribe
RedisPubSub.subscribe("order.created", orderId -> {
    orderService.create(orderId);
    RedisPubSub.publish("payment.requested", orderId);  // 다음 단계 트리거
});

RedisPubSub.subscribe("payment.requested", orderId -> {
    paymentService.process(orderId);
    RedisPubSub.publish("inventory.reserve", orderId);
});

RedisPubSub.subscribe("inventory.reserve", orderId -> {
    inventoryService.reserve(orderId);
    RedisPubSub.publish("shipping.request", orderId);
});

RedisPubSub.subscribe("shipping.request", orderId -> {
    shippingService.ship(orderId);
});

// 주문 생성 시작
RedisPubSub.publish("order.created", "ORD-12345");
// → 4개의 클래스가 순차적으로 자동 실행됨 ✅
```

---

### 시나리오 3: 캐시 무효화 (분산 시스템)

```java
// 여러 서버에서 동일한 Subscribe 실행
CacheManager cacheManager = new CacheManager();

RedisPubSub.Subscriber subscriber = RedisPubSub.subscribe("cache.invalidate", cacheKey -> {
    cacheManager.remove(cacheKey);  // ✅ 모든 서버에서 동시 실행
    System.out.println("캐시 삭제: " + cacheKey);
});

// 한 서버에서 캐시 무효화 발행
RedisPubSub.publish("cache.invalidate", "user:1001:profile");
// → 모든 서버의 캐시가 동시에 삭제됨 ✅
```

---

## ⚠️ 중요 주의사항

### 1. Subscriber는 반드시 close() 호출

```java
// ❌ 나쁜 예
RedisPubSub.subscribe("channel", msg -> {});
// 메모리 누수!

// ✅ 좋은 예
RedisPubSub.Subscriber subscriber = RedisPubSub.subscribe("channel", msg -> {});
// ... 사용 ...
subscriber.close();  // 반드시 호출
```

---

### 2. 예외 처리

```java
RedisPubSub.Subscriber subscriber = RedisPubSub.subscribe("tasks", message -> {
    try {
        processor.process(message);
    } catch (Exception e) {
        logger.error("작업 실패: " + message, e);
        // 개별 메시지 실패가 전체 Subscribe를 중단하지 않도록
    }
});
```

---

### 3. 장시간 작업은 별도 스레드로

```java
ExecutorService executor = Executors.newFixedThreadPool(10);

RedisPubSub.Subscriber subscriber = RedisPubSub.subscribe("heavy-tasks", message -> {
    // 메시지 핸들러는 빠르게 반환해야 함
    executor.submit(() -> {
        // 시간이 오래 걸리는 작업은 별도 스레드에서
        heavyProcessor.process(message);
    });
});
```

---

## 🔄 실행 순서

### 방법 1: 단일 프로세스에서 테스트

```bash
# 예제 파일 실행 (Subscribe + Publish 모두 포함)
java RedisPubSubSimpleExample

# 또는
java RedisPubSubWorker

# 또는
java RedisPubSubEventBus
```

---

### 방법 2: 실제 분산 환경 테스트

**터미널 1 (Subscriber - 메시지 수신 대기)**
```java
DataProcessor processor = new DataProcessor();

RedisPubSub.Subscriber subscriber = RedisPubSub.subscribe("tasks", message -> {
    processor.process(message);
});

System.out.println("메시지 대기 중...");
Thread.sleep(60000); // 1분간 대기
```

**터미널 2 (Publisher - 메시지 발송)**
```java
RedisPubSub.publish("tasks", "task-001");
RedisPubSub.publish("tasks", "task-002");
RedisPubSub.publish("tasks", "task-003");
```

→ 터미널 1에서 DataProcessor가 자동으로 3번 실행됨 ✅

---

## 🎯 정리

### 핵심 포인트

1. **RedisPubSub.subscribe()** - 메시지를 받아 특정 클래스 실행
2. **람다로 클래스 메서드 연결** - `msg -> myClass.method(msg)`
3. **JSON 지원** - 복잡한 객체도 전달 가능
4. **반드시 close()** - 리소스 누수 방지

### 사용 예

```java
// 가장 간단한 패턴
MyClass myClass = new MyClass();
RedisPubSub.Subscriber sub = RedisPubSub.subscribe("channel", msg -> {
    myClass.execute(msg);  // ✅ 메시지 받으면 자동 실행
});
```

모든 예제가 **순수 Java**로 작성되어 있으며, **Spring 없이** 바로 실행 가능합니다! 🚀
