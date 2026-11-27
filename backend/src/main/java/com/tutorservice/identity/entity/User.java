package com.tutorservice.identity.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="users", schema="identity")
public class User {
    public enum Role { STUDENT, TEACHER }
    @Id private UUID id;
    @Column(name="normalized_email", nullable=false) private String email;
    @Column(name="password_hash", nullable=false) private String passwordHash;
    @Column(nullable=false) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private Role role;
    @Column(nullable=false) private String status;
    @Column(name="session_version", nullable=false) private long sessionVersion;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    protected User() {}
    public User(String email, String passwordHash, String name, Role role) {
        this.id=UUID.randomUUID(); this.email=email; this.passwordHash=passwordHash; this.name=name;
        this.role=role; this.status="ACTIVE"; this.createdAt=Instant.now();
    }
    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getName() { return name; }
    public Role getRole() { return role; }
    public long getSessionVersion() { return sessionVersion; }
    public boolean active() { return "ACTIVE".equals(status); }
    public void rename(String name) { this.name=name; }
    public void delete() {
        status="DELETED"; sessionVersion++; email=id+"@deleted.invalid"; name="탈퇴 회원"; passwordHash="DELETED";
    }
}
