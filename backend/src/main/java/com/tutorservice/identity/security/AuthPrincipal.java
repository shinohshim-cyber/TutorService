package com.tutorservice.identity.security;

import java.util.UUID;

public record AuthPrincipal(UUID userId, UUID family, long version) {}
