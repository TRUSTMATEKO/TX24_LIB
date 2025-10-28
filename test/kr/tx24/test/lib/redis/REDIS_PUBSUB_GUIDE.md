# RedisPubSub.java 사용 가이드

## 📋 개요

`RedisPubSub.java`는 Redis Pub/Sub 기능을 위한 전용 Connection 관리 클래스입니다.
일반 Redis 작업(`get/set`)과는 별도로 Pub/Sub Connection을 관리하여 효율적인 메시지 발행/구독을 지원합니다.

### 왜 별도 클래스인가?

1. **Connection 특성 차이**
   - 일반 작업: 짧은 요청-응답 패턴, Connection 재사용
   - Pub/Sub: 장기 연결 유지 필요 (특히 Subscriber)

2. **리소스 관리**
   - Subscribe는 장기 연결이므로 명시적 관리 필요
   - Publish는 일회성 작업이므로 Connection 자동 생성/해제

3. **성능 최적화**
   - Pub/Sub 전용 Connection Pool
   - 불필요한 Connection 낭비 방지

---

## 🚀 주요 기능

### 1. 간편 API (일회성 Publish)

```java
// String 메시지 발행
Long count = RedisPubSub.publish("channel:news", "Breaking News!");

// JSON 객체 발행
UserEvent event = new UserEvent(1001, "LOGIN");
RedisPubSub.publishJson("channel:users", event);
```

**특징:**
- Connection 자동 생성/해제
- 간단한 메시지 발행에 최적
- 반환값: 메시지를 받은 구독자 수

---

### 2. 재사용 가능한 Publisher

```java
// try-with-resources로 자동 정리
try (RedisPubSub.Publisher publisher = RedisPubSub.createPublisher()) {
    publisher.publish("channel:logs", "Log 1");
    publisher.publish("channel:logs", "Log 2");
    publisher.publish("channel:logs", "Log 3");
} // Connection 자동 close
```

**사용 시나리오:**
- 여러 메시지를 연속으로 발행
- 하나의 Connection 재사용으로 성능 향상
- Batch 작업에 적합

---

### 3. Subscriber (장기 연결)

```java
// 메시지 구독
RedisPubSub.Subscriber subscriber = RedisPubSub.subscribe("channel:alerts", message -> {
    System.out.println("수신: " + message);
    // 메시지 처리 로직
});

// ... 애플리케이션 실행 중 메시지 수신 ...

// 구독 해제
subscriber.unsubscribe();
subscriber.close();
```

**주의사항:**
- ⚠️ **반드시 `close()` 호출 필요** (리소스 누수 방지)
- Connection이 유지되므로 메모리 관리 중요
- Spring Bean으로 관리 시 `@PreDestroy`에서 close

---

### 4. JSON Pub/Sub

```java
// JSON 구독
RedisPubSub.Subscriber subscriber = RedisPubSub.subscribeJson(
    "channel:users",
    UserEvent.class,
    event -> {
        System.out.printf("User %d: %s%n", event.userId, event.action);
    }
);

// JSON 발행
UserEvent event = new UserEvent(1001, "LOGIN", System.currentTimeMillis());
RedisPubSub.publishJson("channel:users", event);
```

**장점:**
- 자동 직렬화/역직렬화
- 타입 안전성 보장
- 복잡한 객체 전송 가능

---

### 5. 패턴 구독 (와일드카드)

```java
// order:* 패턴으로 여러 채널 동시 구독
RedisPubSub.Subscriber subscriber = RedisPubSub.psubscribe("order:*", message -> {
    System.out.println("주문 이벤트: " + message);
});

// 다양한 채널에 메시지 발행
RedisPubSub.publish("order:created", "Order #1234 created");
RedisPubSub.publish("order:shipped", "Order #1234 shipped");
RedisPubSub.publish("order:delivered", "Order #1234 delivered");
// 모두 위 Subscriber가 수신
```

**활용:**
- 마이크로서비스 간 이벤트 버스
- 도메인별 메시지 그룹핑
- 유연한 메시지 라우팅

---

## 📊 유틸리티 메서드

```java
// 특정 채널의 구독자 수 확인
Long count = RedisPubSub.countSubscribers("channel:news");

// 활성 채널 목록
List<String> channels = RedisPubSub.getActiveChannels();

// 현재 관리 중인 Subscriber 수
int subscriberCount = RedisPubSub.getActiveSubscriberCount();
```

---

## 🔒 Thread Safety

- ✅ **모든 메서드는 Thread-Safe**
- ✅ 멀티스레드 환경에서 안전하게 사용 가능
- ✅ Publisher/Subscriber 동시 사용 가능

```java
// 멀티스레드 환경
ExecutorService executor = Executors.newFixedThreadPool(10);

// 여러 스레드에서 동시에 발행 가능
for (int i = 0; i < 100; i++) {
    final int index = i;
    executor.submit(() -> {
        RedisPubSub.publish("channel:logs", "Message " + index);
    });
}
```

---

## 🎯 Best Practices

### 1. Subscriber 리소스 관리

```java
// ❌ 나쁜 예: close 하지 않음
public void badExample() {
    RedisPubSub.subscribe("channel", msg -> {});
    // 메모리 누수 발생!
}

// ✅ 좋은 예: try-finally로 보장
public void goodExample() {
    RedisPubSub.Subscriber subscriber = null;
    try {
        subscriber = RedisPubSub.subscribe("channel", msg -> {});
        // ... 로직 ...
    } finally {
        if (subscriber != null) {
            subscriber.close();
        }
    }
}

// ✅ 더 좋은 예: Spring Bean 관리
@Component
public class MessageListener {
    
    private RedisPubSub.Subscriber subscriber;
    
    @PostConstruct
    public void init() {
        subscriber = RedisPubSub.subscribe("channel", this::handleMessage);
    }
    
    @PreDestroy
    public void cleanup() {
        if (subscriber != null) {
            subscriber.close();
        }
    }
    
    private void handleMessage(String message) {
        // 메시지 처리
    }
}
```

### 2. Publisher 선택 가이드

```java
// 단일 메시지 → 간편 API
RedisPubSub.publish("channel", "message");

// 여러 메시지 → 재사용 Publisher
try (RedisPubSub.Publisher pub = RedisPubSub.createPublisher()) {
    for (String msg : messages) {
        pub.publish("channel", msg);
    }
}
```

### 3. 에러 처리

```java
RedisPubSub.Subscriber subscriber = RedisPubSub.subscribe("channel", message -> {
    try {
        // 메시지 처리
        processMessage(message);
    } catch (Exception e) {
        // 개별 메시지 처리 실패가 구독을 중단하지 않도록
        logger.error("Failed to process message", e);
    }
});
```

---

## 🔧 설정

### Redis URI 설정

`SystemUtils.getRedisSystemUri()`를 통해 Redis 연결 정보 제공 필요:

```properties
# application.properties
redis.uri=redis://localhost:6379
# 또는 인증 포함
redis.uri=redis://password@localhost:6379/0
```

### Connection Pool 튜닝

필요시 `RedisPubSub.java` 내부 설정 수정:

```java
clientResources = DefaultClientResources.builder()
    .ioThreadPoolSize(2)          // I/O 스레드 (Pub/Sub는 I/O 집약적)
    .computationThreadPoolSize(2)  // 계산 스레드
    .build();
```

---

## 📦 의존성

```xml
<!-- Lettuce (Redis Client) -->
<dependency>
    <groupId>io.lettuce</groupId>
    <artifactId>lettuce-core</artifactId>
    <version>6.2.x</version>
</dependency>

<!-- Jackson (JSON) -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.15.x</version>
</dependency>

<!-- Jackson Java 8+ 날짜/시간 지원 -->
<dependency>
    <groupId>com.fasterxml.jackson.datatype</groupId>
    <artifactId>jackson-datatype-jsr310</artifactId>
    <version>2.15.x</version>
</dependency>
```

---

## 🆚 Redis.java vs RedisPubSub.java

| 기능 | Redis.java | RedisPubSub.java |
|-----|-----------|-----------------|
| 용도 | GET/SET 등 일반 작업 | Pub/Sub 전용 |
| Connection | 싱글톤 재사용 | 필요시 생성/해제 |
| Close | ❌ 불필요 (계속 재사용) | ✅ 필수 (Subscriber) |
| 사용 패턴 | 짧은 요청-응답 | 장기 연결 (Subscribe) |

---

## 🎓 마이그레이션 가이드

### Before (Redis.java 사용 시도)

```java
// ❌ Redis.java로 Pub/Sub 시도 (권장하지 않음)
StatefulRedisConnection<String, String> conn = Redis.getString();
// Pub/Sub 설정 복잡...
// Connection 관리 어려움...
```

### After (RedisPubSub.java 사용)

```java
// ✅ 명확하고 간단
RedisPubSub.publish("channel", "message");

RedisPubSub.Subscriber subscriber = RedisPubSub.subscribe("channel", msg -> {
    // 처리
});
```

---

## 🔍 Troubleshooting

### Q1: "Subscriber already closed" 에러

**원인:** 이미 닫힌 Subscriber를 재사용 시도

**해결:**
```java
// ❌
subscriber.close();
subscriber.unsubscribe(); // 에러 발생!

// ✅
subscriber.unsubscribe();
subscriber.close();
```

### Q2: 메시지가 수신되지 않음

**체크리스트:**
1. Subscriber가 정상적으로 구독했는지 확인 (로그 확인)
2. 채널명이 정확한지 확인
3. Publisher와 Subscriber가 같은 Redis 서버에 연결되었는지 확인
4. 방화벽/네트워크 문제 확인

```java
// 디버깅: 구독자 수 확인
Long count = RedisPubSub.countSubscribers("channel:test");
System.out.println("구독자 수: " + count); // 0이면 문제 있음
```

### Q3: 메모리 사용량 증가

**원인:** Subscriber를 close 하지 않음

**해결:**
```java
// ✅ 반드시 close
try (RedisPubSub.Subscriber sub = RedisPubSub.subscribe(...)) {
    // 사용
} // 자동 close
```

---

## 📚 참고 자료

- [Lettuce 공식 문서 - Pub/Sub](https://lettuce.io/core/release/reference/index.html#pubsub)
- [Redis Pub/Sub 가이드](https://redis.io/topics/pubsub)
- Jackson ObjectMapper 설정

---

## 🎉 결론

`RedisPubSub.java`는 Redis Pub/Sub를 위한 **간편하고 안전한** 인터페이스를 제공합니다.

**핵심 원칙:**
1. ✅ Publisher는 자동 관리 (간편 API 또는 try-with-resources)
2. ✅ Subscriber는 명시적 관리 (반드시 close 호출)
3. ✅ Redis.java와 분리하여 각자 최적화된 Connection 관리

**언제 사용?**
- 마이크로서비스 간 이벤트 전파
- 실시간 알림 시스템
- 분산 시스템의 메시지 브로커
- 캐시 무효화 이벤트 전파
