# TX24_LIB

TX24 서비스에서 공통으로 사용하는 Java 라이브러리입니다. 범용 유틸리티와 데이터베이스·Redis 연동 기능, Netty 기반 INet 서버, 애노테이션 기반 작업 스케줄러를 제공합니다.

## 패키지 구성

| 패키지 | 설명 |
| --- | --- |
| `kr.tx24.lib.*` | 애플리케이션 공통 유틸리티와 외부 시스템 연동 |
| `kr.tx24.inet.*` | Netty 기반 INet 프로토콜 서버와 라우팅 |
| `kr.tx24.task.*` | 애노테이션 기반 주기 작업 탐색과 실행 |

## 공통 라이브러리 (`kr.tx24.lib`)

### `kr.tx24.lib.cipher`

대칭키·공개키 암호화와 메시지 인증을 지원합니다.

- `AESCipher`: AES 암·복호화
- `HMAC`: HMAC 생성과 검증
- `RSA`, `RSAUtils`: RSA 키 표현과 암·복호화 보조 기능
- `Seed`: SEED 암호화

### `kr.tx24.lib.conf`

- `Configure`: 설정 파일과 시스템 설정값을 읽고 관리하는 공통 설정 객체입니다.

### `kr.tx24.lib.crypt`

비밀번호와 문자열의 단방향 해싱 및 레거시 암호화를 제공합니다.

- `Argon2`: Argon2 기반 비밀번호 해싱·검증. `fastHash` 계열은 처리 비용을 낮춘 모드이므로 보안이 중요한 비밀번호 저장에는 사용하지 않는 것이 좋습니다.
- `BCrypt`: BCrypt 해싱·검증
- `CCrypt`: CBC/ECB 방식의 DES, 3DES 등 JCE 기반 암·복호화와 메시지 다이제스트

### `kr.tx24.lib.db`

JDBC 연결, 세션, 트랜잭션과 CRUD 실행을 추상화합니다.

- `DBManager`, `DBFactory`: 데이터베이스 연결 설정과 객체 생성
- `DBSession`: `AutoCloseable` JDBC 세션. try-with-resources 사용을 권장합니다.
- `DBTrx`, `DBTrxUpdate`: 트랜잭션 및 갱신 작업 처리
- `Create`, `Retrieve`, `Update`, `Delete`: CRUD SQL 실행
- `RecordSet`: 조회 결과 표현
- `DBUtils`, `DBType`, `DBException`: DB 공통 기능, DB 종류, 전용 예외

### `kr.tx24.lib.db.scheme`

데이터베이스 메타데이터와 SQL 결과를 표현하는 모델입니다.

- `Catalog`, `Table`, `Column`: 카탈로그·테이블·컬럼 구조
- `SqlResult`: SQL 실행 결과

### `kr.tx24.lib.executor`

- `AsyncExecutor`: 공용 스레드 풀에서 비동기 작업, 지연 작업, 고정 주기 작업을 실행합니다. 애플리케이션 종료 시 관련 자원도 함께 정리해야 합니다.

### `kr.tx24.lib.http.ua`

- `UADetect`: HTTP User-Agent 문자열을 분석합니다.
- `UserAgent`: 분석한 브라우저·운영체제·장치 정보를 담습니다.

### `kr.tx24.lib.inter`

- `INet`: INet 요청과 응답의 헤더·데이터를 운반하는 직렬화 가능 프로토콜 모델입니다. `kr.tx24.inet.codec`과 `kr.tx24.inet.handler`가 이 객체를 사용합니다.

### `kr.tx24.lib.jsoup`

- `JsoupUtils`: Jsoup을 이용한 HTML 파싱, 텍스트 추출 및 문서 처리 유틸리티입니다.

### `kr.tx24.lib.kms`

- `KMSUtils`: KMS 연동에 필요한 키와 암호화 데이터 처리 기능을 제공합니다.

### `kr.tx24.lib.lang`

문자열, 숫자, 날짜, 네트워크 등 자주 쓰는 범용 기능을 모은 패키지입니다.

| 영역 | 클래스 | 주요 기능 |
| --- | --- | --- |
| 공통·시스템 | `CommonUtils`, `SystemUtils`, `SecurityUtils` | 값 검사·변환, 시스템 초기화와 환경 정보, 보안 관련 보조 기능 |
| 문자열 | `Abbreviator`, `EmojiUtils`, `MaskUtils`, `MsgUtils` | 문자열 축약, 이모지 처리, 개인정보 마스킹, 메시지 가공 |
| 패턴 | `PatternUtils`, `RegExUtils` | 정규식 패턴과 문자열 검증·치환 |
| 날짜·숫자 | `DateUtils`, `BDUtils`, `DecimalUtils`, `CalcUtils`, `CompareUtils` | 날짜 계산, `BigDecimal` 및 숫자 계산·비교 |
| 식별자·비트 | `IDUtils`, `BitmapUtils`, `ByteBufferUtils` | ID 생성, 비트맵, 바이트 버퍼 변환 |
| 네트워크 | `NetUtils`, `IpMatcherUtils`, `URIUtils` | 네트워크 정보, IP 범위 매칭, URI/URL 및 쿼리 문자열 처리 |

`SystemUtils.init()`은 시스템 속성, 로깅, 실행 환경 등 다른 공통 기능의 전제가 되므로 서버 시작 초기에 호출하는 것이 좋습니다.

### `kr.tx24.lib.lb`

- `LoadBalancer`: 등록된 대상 중 사용할 노드를 선택하고 시스템의 다중 엔드포인트 구성을 지원합니다.

### `kr.tx24.lib.lifecycle`

JVM과 애플리케이션의 시작·종료 생명주기를 관리합니다.

- `SystemManager`: 종료 처리를 담당하는 관리 스레드
- `ShutdownManager`: 종료 가능한 컴포넌트의 계약
- `JvmStatusManager`: JVM 상태와 자원 사용 정보 관리

### `kr.tx24.lib.logback`

Logback 설정과 로그 후처리를 확장합니다.

- `LogBackConfigure`: Logback 초기 설정기
- `LogUtils`: 로거와 로그 레벨 관련 공통 기능
- `MaskConverter`: 로그 메시지의 민감정보 마스킹
- `RedisAppender`: 로그 이벤트를 Redis로 전송하는 Appender

### `kr.tx24.lib.map`

타입 변환이 편리한 Map과 캐시 자료구조를 제공합니다.

- `LinkedMap`: 입력 순서를 보존하는 확장 Map
- `SharedMap`: 동시 접근을 지원하는 확장 Map
- `ThreadSafeLinkedMap`: 순서를 보존하면서 동시 접근을 제어하는 Map
- `TimeoutCache`, `TimeoutCacheMap`: 만료 시간을 갖는 캐시
- `MapFactory`: 용도와 예상 크기에 맞는 Map 생성
- `TypeRegistry`: Map 및 값 변환에 사용하는 지원 타입 목록

### `kr.tx24.lib.mapper`

Jackson 기반 데이터 직렬화·역직렬화를 형식별로 제공합니다.

- `JacksonAbstract`: 공통 ObjectMapper 설정과 변환 기반 클래스
- `JacksonUtils`: JSON
- `JacksonXmlUtils`: XML
- `JacksonYamlUtils`: YAML
- `JacksonCsvUtils`: CSV

### `kr.tx24.lib.netty`

- `NettyUtils`: Netty 채널의 주소, 연결 상태, 전송과 종료 처리에 사용하는 보조 기능입니다.

### `kr.tx24.lib.otp`

- `TOTPUtils`: 시간 기반 일회용 비밀번호(TOTP)의 시크릿·인증 코드 생성과 검증을 지원합니다.

### `kr.tx24.lib.redis`

Lettuce 기반 Redis 연결과 자료형별 편의 기능을 제공합니다.

| 클래스 | 설명 |
| --- | --- |
| `Redis` | 공유 `StatefulRedisConnection`의 초기화, 동기·비동기 명령, PING, 상태 확인과 종료를 담당하는 연결 관리자 |
| `TypedRedisCodec` | `String` 키와 여러 Java 값 타입을 Redis 데이터로 변환하는 Codec |
| `RedisUtils` | 객체 저장·조회, 키와 만료 시간 등 Redis 공통 명령 |
| `RedisText`, `RedisTextUtils` | 문자열 데이터 저장·조회와 텍스트 중심 편의 기능 |
| `RedisPopUtils` | 목록·큐 데이터의 pop 처리 |
| `RedisPubSub` | 채널 구독, 발행과 구독 해제 |
| `RedisHeartbeat` | 연결 유지를 위한 주기적 heartbeat |

Redis 주소는 JVM 시스템 속성 `REDIS`를 우선 사용하며, 없으면 `REDIS1`, `REDIS2`를 조합합니다. 값은 `host:port` 형식이며 `SystemUtils.REDIS_CACHE_KEY`가 설정된 경우 URI의 인증정보로 사용됩니다.

```bash
java -DREDIS=127.0.0.1:6379 ...
```

```java
import kr.tx24.lib.redis.Redis;

Redis.init();
try {
    Redis.sync().set("sample:key", "value");
    Object value = Redis.sync().get("sample:key");
} finally {
    Redis.shutdown();
}
```

`Redis`는 프로세스 단위 공유 연결을 사용하고 30초 간격으로 PING을 전송합니다. Lettuce 자동 재연결이 활성화되어 있으며, `shutdown()` 이후에는 같은 JVM에서 다시 초기화할 수 없습니다.

### `kr.tx24.lib.shared`

서비스 사이에서 전달하는 메시지 모델입니다.

- `Email`, `EmailTmpl`: 이메일과 이메일 템플릿 데이터
- `Sms`: SMS 발송 데이터
- `MsgResult`: 메시지 발송 결과

### `kr.tx24.lib.zip`

- `ZipUtils`: ZIP 압축·해제
- `GzipUtils`: GZIP 압축·해제

## INet 서버 (`kr.tx24.inet`)

### `kr.tx24.inet.codec`

- `INetDecoder`: 네트워크 바이트 스트림을 `INet` 객체로 디코딩합니다.
- `INetEncoder`: `INet` 객체를 전송 가능한 바이트 스트림으로 인코딩합니다.

### `kr.tx24.inet.conf`

- `INetConfigLoader`: 설정 파일을 읽어 INet 서버 설정을 로드하고 타입별로 조회합니다.

### `kr.tx24.inet.handler`

- `INetHandler`: 디코딩된 요청을 라우터에 전달하고 반환값이나 예외를 INet 응답으로 변환하는 Netty 인바운드 핸들러입니다.

### `kr.tx24.inet.mapper`

컨트롤러와 메서드의 라우팅·데이터 바인딩을 선언하는 런타임 애노테이션입니다.

- `@Controller`: 라우팅 대상 클래스
- `@Route`: 요청 경로 또는 명령과 처리 메서드 매핑
- `@Autowired`: 컨트롤러 의존성 주입 대상
- `@Head`, `@Data`: INet 헤더·데이터를 메서드 인자에 바인딩
- `@Description`: 라우트 설명 메타데이터

### `kr.tx24.inet.route`

- `Router`: 컨트롤러와 라우트를 검색·등록하고 요청에 맞는 처리기를 찾습니다.
- `RouteInvoker`: 선택한 컨트롤러 메서드의 인자를 구성하고 호출합니다.
- `ThreadLocalContext`: 요청 처리 중 필요한 컨텍스트를 현재 스레드에 보관합니다.

### `kr.tx24.inet.server`

- `INetServer`: Netty 서버를 구성하고 codec과 handler 파이프라인을 설치하여 INet 요청을 수신합니다.

### `kr.tx24.inet.util`

- `INetUtils`: INet 요청 데이터 처리 보조 기능
- `INetRespUtils`: 정상·오류 응답 생성 보조 기능

요청은 다음 순서로 처리됩니다.

```text
Socket -> INetDecoder -> INetHandler -> Router -> RouteInvoker -> Controller
       <- INetEncoder <- INetHandler <- 응답 객체 또는 오류 ----------
```

## 작업 스케줄러 (`kr.tx24.task`)

### `kr.tx24.task.annotation`

- `@Task`: `Runnable` 구현 클래스에 이름, 실행 시각, 반복 주기, 요일, 시작·종료일, 활성화 여부와 우선순위를 선언합니다. 반복 방식은 작업 시작 시각 기준 `RATE`와 작업 완료 후 기준 `DELAY`를 지원합니다.

### `kr.tx24.task.config`

- `TaskConfig`: `@Task` 설정을 검증 가능한 값 객체로 변환하고 다음 실행 시각을 계산합니다.

### `kr.tx24.task.main`

- `TaskScanner`: 클래스패스에서 `@Task`가 지정된 작업을 검색합니다.
- `TaskScheduler`: 검색한 작업의 최초 실행 시각과 반복 주기를 계산해 스케줄링합니다.
- `TaskLauncher`: 작업 검색과 스케줄러 시작을 연결하는 진입점입니다.

```java
import kr.tx24.task.annotation.Task;

@Task(
    name = "cacheRefresh",
    time = "09:00",
    period = "1d",
    desc = "일일 캐시 갱신"
)
public class CacheRefreshTask implements Runnable {
    @Override
    public void run() {
        // 작업 내용
    }
}
```

지원 주기는 월 단위 `M`, 주 `w`, 일 `d`, 시간 `h`, 분 `m`, 초 `s`입니다. 실행 시간이 일정하지 않거나 작업 중첩을 피해야 할 때는 기본값인 `DELAY`가 적합합니다.
