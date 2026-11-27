package com.tutorservice.relationship.repository;

import com.tutorservice.relationship.entity.*;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;

@Repository
public class RelationshipRepository {
    private final JdbcTemplate jdbc;
    public RelationshipRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private static final RowMapper<Invitation> INVITE=(r,n)->new Invitation(r.getObject("id",UUID.class),r.getObject("sender_id",UUID.class),r.getObject("recipient_id",UUID.class),r.getTimestamp("expires_at").toInstant(),r.getTimestamp("consumed_at")==null?null:r.getTimestamp("consumed_at").toInstant());
    private static final RowMapper<Relationship> RELATION=(r,n)->new Relationship(r.getObject("id",UUID.class),r.getObject("teacher_id",UUID.class),r.getObject("student_id",UUID.class),r.getString("status"),r.getTimestamp("accepted_at").toInstant());
    public Invitation create(UUID teacher,UUID student) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO identity.relationship_invitations(id,sender_id,recipient_id,expires_at) VALUES (?,?,?,now()+interval '24 hours')",id,teacher,student);
        return invitation(id,false).orElseThrow();
    }
    public Optional<Invitation> invitation(UUID id,boolean lock) { return jdbc.query("SELECT * FROM identity.relationship_invitations WHERE id=?"+(lock?" FOR UPDATE":""),INVITE,id).stream().findFirst(); }
    public List<Invitation> invitations(UUID user,UUID after,int limit) {
        return jdbc.query("SELECT i.* FROM identity.relationship_invitations i JOIN identity.users s ON s.id=i.sender_id JOIN identity.users r ON r.id=i.recipient_id WHERE (i.sender_id=? OR i.recipient_id=?) AND i.id>? AND i.consumed_at IS NULL AND i.expires_at>now() AND s.status='ACTIVE' AND r.status='ACTIVE' ORDER BY i.id LIMIT ?",INVITE,user,user,after,limit+1);
    }
    public List<Relationship> relations(UUID user,UUID after,int limit) {
        return jdbc.query("SELECT r.* FROM identity.teacher_student_relations r JOIN identity.users t ON t.id=r.teacher_id JOIN identity.users s ON s.id=r.student_id WHERE (r.teacher_id=? OR r.student_id=?) AND r.id>? AND r.status='ACTIVE' AND t.status='ACTIVE' AND s.status='ACTIVE' ORDER BY r.id LIMIT ?",RELATION,user,user,after,limit+1);
    }
    public boolean exists(UUID teacher,UUID student) { return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM identity.teacher_student_relations WHERE teacher_id=? AND student_id=? AND status='ACTIVE')",Boolean.class,teacher,student)); }
    public Relationship accept(Invitation i) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO identity.teacher_student_relations(id,teacher_id,student_id,status,invitation_id) VALUES (?,?,?,'ACTIVE',?)",id,i.senderId(),i.recipientId(),i.id());
        jdbc.update("UPDATE identity.relationship_invitations SET consumed_at=now() WHERE id=?",i.id());
        return relation(id).orElseThrow();
    }
    public Optional<Relationship> relation(UUID id) { return jdbc.query("SELECT * FROM identity.teacher_student_relations WHERE id=?",RELATION,id).stream().findFirst(); }
    public void revoke(UUID id) { jdbc.update("UPDATE identity.teacher_student_relations SET status='REVOKED' WHERE id=?",id); }
}
