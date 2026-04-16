package com.peoplecore.exception;

import com.peoplecore.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        ErrorResponse response = new ErrorResponse(errorCode.getStatus(), errorCode.name(), errorCode.getMessage());
        return ResponseEntity.status(errorCode.getStatus()).body(response);
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity
                .status(400)
                .body(Map.of(
                        "message", e.getMessage(),
                        "timestamp", LocalDateTime.now()
                ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException e) {
        return ResponseEntity
                .status(409)
                .body(Map.of(
                        "message", e.getMessage(),
                        "timestamp", LocalDateTime.now()
                ));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(BusinessException e) {
        return ResponseEntity
                .status(e.getStatus())
                .body(Map.of(
                        "message", e.getMessage(),
                        "timestamp", LocalDateTime.now()
                ));
    }

    // 클라이언트가 SSE·async 응답 중에 연결을 끊은 경우 — 바디를 쓰면 2차 에러이므로 null 반환
    @ExceptionHandler({AsyncRequestNotUsableException.class, ClientAbortException.class})
    public ResponseEntity<Void> handleClientDisconnect(Exception e) {
        log.warn("Client disconnected during async/SSE response: {}", e.getMessage());
        return null;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception e) {
        if (e instanceof AsyncRequestNotUsableException || e instanceof ClientAbortException) {
            log.warn("Client disconnected (wrapped): {}", e.getMessage());
            return null;
        }
        log.error("Unhandled exception", e);
        return ResponseEntity
                .status(500)
                .body(Map.of(
                        "message", "서버 내부 오류가 발생했습니다.",
                        "timestamp", LocalDateTime.now()
                ));
    }
}