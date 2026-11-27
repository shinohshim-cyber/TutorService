package com.tutorservice.relationship.entity;
import java.time.Instant;
import java.util.UUID;
public record Relationship(UUID id, UUID teacherId, UUID studentId, String status, Instant acceptedAt) {}
