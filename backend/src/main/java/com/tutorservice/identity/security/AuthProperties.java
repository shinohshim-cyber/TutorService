package com.tutorservice.identity.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tutor.auth")
public record AuthProperties(String jwtKey, String jwtKid, String previousKey, String previousKid,
                             String refreshHmacKey, String issuer, String audience, boolean secureCookie,
                             List<String> allowedOrigins) {}
