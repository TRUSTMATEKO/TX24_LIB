package kr.tx24.test.was.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;

@ControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<String> handleNoHandlerFound(NoHandlerFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body("Custom 404 (NoHandlerFoundException) - URL: " + ex.getRequestURL());
    }

    // (2) 기본 404 처리 (예외 안 던지는 경우 - 직접 ResponseStatusExceptionResolver와 매핑)
    @ExceptionHandler
    public ResponseEntity<String> handleAnyOther(Exception ex) {
        if (ex instanceof org.springframework.web.servlet.resource.NoResourceFoundException) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("Custom 404 (NoResourceFoundException) - Resource Not Found");
        }
        // 그 외 예외는 500 처리
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Custom 500 - " + ex.getMessage());
    }
}
