package kr.tx24.api.mapper;

import kr.tx24.api.util.ApiCode;
import kr.tx24.lib.lang.DateUtils;

/**
 * API 응답 클래스
 * 
 * @param <T> 응답 데이터 타입
 */
public class ApiResponse<T> {
    private String code;      // 응답 코드
    private String message;   // 응답 메시지
    private T data;          // 응답 데이터
    private String create;   // 생성 일시
    
    
    public ApiResponse() {
        this.create = DateUtils.getCurrentDate();
    }
    
    public ApiResponse(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.create = DateUtils.getCurrentDate();
    }
  
    
    /**
     * 성공 응답 (data만)
     */
    public static <T> ApiResponse<T> success(T data) {
        return success(ApiCode.SUCCESS.getMessage(), data);
    }
    
    /**
     * 성공 응답 (커스텀 메시지 + data)
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>("success", message, data);
    }
    
    
    /**
     * 유효성 검증 실패 (기본 메시지)
     */
    public static <T> ApiResponse<T> invalid(ApiCode apiCode) {
        return createResponse("invalid", apiCode, null, false);
    }
    
    /**
     * 유효성 검증 실패 (커스텀 메시지 - 대체)
     */
    public static <T> ApiResponse<T> invalid(ApiCode apiCode, String message) {
        return createResponse("invalid", apiCode, message, false);
    }
    
    /**
     * 유효성 검증 실패 (커스텀 메시지 - 추가)
     */
    public static <T> ApiResponse<T> invalidAdd(ApiCode apiCode, String message) {
        return createResponse("invalid", apiCode, message, true);
    }
    
   
    /**
     * 비즈니스 실패 (기본 메시지)
     */
    public static <T> ApiResponse<T> failure(ApiCode apiCode) {
        return createResponse("failure", apiCode, null, false);
    }
    
    /**
     * 비즈니스 실패 (커스텀 메시지 - 대체)
     */
    public static <T> ApiResponse<T> failure(ApiCode apiCode, String message) {
        return createResponse("failure", apiCode, message, false);
    }
    
    /**
     * 비즈니스 실패 (커스텀 메시지 - 추가)
     */
    public static <T> ApiResponse<T> failureAdd(ApiCode apiCode, String message) {
        return createResponse("failure", apiCode, message, true);
    }
    
   
    
    /**
     * 서버 오류 (기본 메시지)
     */
    public static <T> ApiResponse<T> error(ApiCode apiCode) {
        return createResponse("error", apiCode, null, false);
    }
    
    /**
     * 서버 오류 (커스텀 메시지 - 대체)
     */
    public static <T> ApiResponse<T> error(ApiCode apiCode, String message) {
        return createResponse("error", apiCode, message, false);
    }
    
    /**
     * 서버 오류 (커스텀 메시지 - 추가)
     */
    public static <T> ApiResponse<T> errorAdd(ApiCode apiCode, String message) {
        return createResponse("error", apiCode, message, true);
    }
    
   
    /**
     * 응답 생성 핵심 로직
     * 
     * @param prefix 접두사 (invalid, failure, error)
     * @param apiCode ApiCode enum
     * @param customMessage 커스텀 메시지 (null이면 기본 메시지 사용)
     * @param appendMessage true면 기본메시지+커스텀메시지, false면 커스텀메시지만
     */
    private static <T> ApiResponse<T> createResponse(
            String prefix, 
            ApiCode apiCode, 
            String customMessage, 
            boolean appendMessage) {
        
        // 코드 생성: prefix_code (예: invalid_request)
        String fullCode = prefix + "_" + apiCode.getCode();
        
        // 메시지 생성
        String finalMessage;
        if (customMessage == null) {
            // 커스텀 메시지 없으면 기본 메시지
            finalMessage = apiCode.getMessage();
        } else if (appendMessage) {
            // 추가 모드: 기본메시지 + 커스텀메시지
            finalMessage = apiCode.getMessage() + "," + customMessage;
        } else {
            // 대체 모드: 커스텀메시지만
            finalMessage = customMessage;
        }
        
        return new ApiResponse<>(fullCode, finalMessage, null);
    }
    

    public String getCode() {
        return code;
    }
    
    public void setCode(String code) {
        this.code = code;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public T getData() {
        return data;
    }
    
    public void setData(T data) {
        this.data = data;
    }
    
    public String getCreate() {
        return create;
    }
    
    public void setCreate(String create) {
        this.create = create;
    }
}