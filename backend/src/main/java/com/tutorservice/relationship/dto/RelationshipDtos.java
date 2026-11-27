package com.tutorservice.relationship.dto;
import com.tutorservice.relationship.entity.*;
import java.time.Instant;
import java.util.UUID;
public final class RelationshipDtos {
    private RelationshipDtos() {}
    public record InvitationView(UUID id, UUID senderId, UUID recipientId, Instant expiresAt) {
        public static InvitationView of(Invitation i) { return new InvitationView(i.id(),i.senderId(),i.recipientId(),i.expiresAt()); }
    }
    public record RelationView(UUID id, UUID teacherId, UUID studentId, String status, Instant acceptedAt) {
        public static RelationView of(Relationship r) { return new RelationView(r.id(),r.teacherId(),r.studentId(),r.status(),r.acceptedAt()); }
    }
}
