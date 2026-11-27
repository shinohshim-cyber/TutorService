package com.tutorservice.identity.service;

import com.tutorservice.common.web.ApiException;
import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
    private PasswordPolicy() {}
    public static void check(String value, boolean signup) {
        if(value==null || value.getBytes(StandardCharsets.UTF_8).length>72 || (signup && value.codePointCount(0,value.length())<12)) throw ApiException.invalid();
    }
}
