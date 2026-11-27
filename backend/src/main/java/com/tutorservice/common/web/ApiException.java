package com.tutorservice.common.web;

public class ApiException extends RuntimeException {
    public final int status;
    public final String code;
    public ApiException(int status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
    public static ApiException unauthorized() { return new ApiException(401, "UNAUTHENTICATED", "다시 로그인해 주세요."); }
    public static ApiException unavailable() { return new ApiException(503, "DEPENDENCY_UNAVAILABLE", "잠시 후 다시 시도해 주세요."); }
    public static ApiException invalid() { return new ApiException(400, "INVALID_INPUT", "입력 내용을 확인해 주세요."); }
    public static ApiException missing() { return new ApiException(404, "NOT_FOUND", "요청한 항목을 찾을 수 없습니다."); }
}
