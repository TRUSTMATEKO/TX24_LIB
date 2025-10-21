package kr.tx24.api.util;

public enum ApiCode {
    
    
    SUCCESS("success", "성공,완료"),
    
   
    //형식 오류 
    REQUEST("request", "잘못된 요청"),
    PARAMETER("parameter", "잘못된 파라미터"),
    FORMAT("format", "데이터 형식 오류"),
    TOKEN("token", "유효하지 않은 토큰"),
    CREDENTIALS("credentials", "잘못된 인증 정보"),
    MISSING_PARAMETER("missing_parameter", "필수 파라미터 누락"),
    MISSING_HEADER("missing_header", "필수 헤더 누락"),
    
    
    
    
    // 인증/인가 실패
    UNAUTHENTICATED("unauthenticated", "인증 필요"),
    AUTHENTICATION_FAILED("authentication_failed", "인증 실패"),
    TOKEN_EXPIRED("token_expired", "토큰 만료"),
    PERMISSION_DENIED("permission_denied", "권한 없음"),
    FORBIDDEN("forbidden", "접근 거부"),
    ACCOUNT_LOCKED("account_locked", "계정 잠김"),
    ACCOUNT_DISABLED("account_disabled", "계정 비활성화"),
    
    
    // 리소스 관련 실패
    NOT_FOUND("not_found", "리소스를 찾을 수 없음"),
    ALREADY_EXISTS("already_exists", "이미 존재함"),
    CONFLICT("conflict", "충돌 발생"),
    DUPLICATE("duplicate", "중복된 요청"),
    
    
    
    
    //ERROR - 서버 내부 오류
    INTERNAL("internal", "내부 서버 오류"),
    DATABASE("database", "데이터베이스 오류"),
   
    EXTERNAL_API("external_api", "외부 API 오류"),
    EXTERNAL_API_TIMEOUT("external_api_timeout", "외부 API 타임아웃"),
    
    SERVICE_UNAVAILABLE("service_unavailable", "서비스 이용 불가"),
    TIMEOUT("timeout", "요청 시간 초과"),
    MAINTENANCE("maintenance", "점검 중");
    
    
    private final String code;
    private final String message;
    
    ApiCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getMessage() {
        return message;
    }
}