package com.tutorservice.identity.repository;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class IdentityCommandRepository {
    private final JdbcTemplate jdbc;
    public IdentityCommandRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public boolean consumeTeacherInvitation(String email,String digest) {
        return jdbc.update("UPDATE identity.teacher_signup_invitations SET consumed_at=now() WHERE normalized_email=? AND digest=? AND consumed_at IS NULL AND expires_at>now()",email,digest)==1;
    }
    public UUID recordDeletion(UUID user) {
        UUID deletion=UUID.randomUUID();
        jdbc.update("UPDATE identity.teacher_student_relations SET status='REVOKED' WHERE (teacher_id=? OR student_id=?) AND status='ACTIVE'",user,user);
        jdbc.update("INSERT INTO identity.account_deletions(id,user_id) VALUES (?,?)",deletion,user);
        return deletion;
    }
}
