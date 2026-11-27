package com.tutorservice.common.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class ApiErrors {
    public record ErrorBody(String code, String message, String traceId, Map<String, Object> details) {}
    public static void write(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status); response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"요청을 처리할 수 없습니다.\",\"traceId\":\"" + UUID.randomUUID() + "\",\"details\":{}}");
    }
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorBody> handle(ApiException e) { return body(e.status, e.code, e.getMessage()); }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class, IllegalArgumentException.class, org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorBody> invalid(Exception e) { return body(400, "INVALID_INPUT", "입력 내용을 확인해 주세요."); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorBody> conflict(Exception e) { return body(409, "CONFLICT", "현재 상태에서는 요청을 처리할 수 없습니다."); }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ErrorBody> dependency(Exception e) { return body(503, "DEPENDENCY_UNAVAILABLE", "잠시 후 다시 시도해 주세요."); }
    private ResponseEntity<ErrorBody> body(int status, String code, String message) {
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(new ErrorBody(code, message, UUID.randomUUID().toString(), Map.of()));
    }
}
