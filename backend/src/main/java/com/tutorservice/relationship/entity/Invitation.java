package com.tutorservice.relationship.entity;
import java.time.Instant;
import java.util.UUID;
public record Invitation(UUID id, UUID senderId, UUID recipientId, Instant expiresAt, Instant consumedAt) {}
