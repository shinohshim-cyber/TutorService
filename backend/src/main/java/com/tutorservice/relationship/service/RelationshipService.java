package com.tutorservice.relationship.service;

import com.tutorservice.common.web.ApiException;
import com.tutorservice.common.web.PageResult;
import com.tutorservice.identity.entity.User;
import com.tutorservice.identity.repository.UserRepository;
import com.tutorservice.identity.security.*;
import com.tutorservice.relationship.dto.RelationshipDtos.*;
import com.tutorservice.relationship.repository.RelationshipRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RelationshipService {
    private final RelationshipRepository relationships; private final UserRepository users; private final RefreshStore sessions;
    public RelationshipService(RelationshipRepository relationships, UserRepository users, RefreshStore sessions) { this.relationships=relationships; this.users=users; this.sessions=sessions; }
    private Map<UUID,User> lock(AuthPrincipal auth,UUID... ids) {
        var result=new HashMap<UUID,User>(); for(var u:users.lockUsers(Arrays.asList(ids))) result.put(u.getId(),u);
        for(UUID id:ids) if(!result.containsKey(id)||!result.get(id).active()) throw ApiException.missing();
        var actor=result.get(auth.userId()); var session=sessions.session(auth.family());
        if(actor==null || actor.getSessionVersion()!=auth.version() || session.version()!=auth.version() || !session.userId().equals(auth.userId())) throw ApiException.unauthorized();
        return result;
    }
    @Transactional
    public InvitationView invite(AuthPrincipal auth,UUID student) {
        var participants=lock(auth,auth.userId(),student);
        if(participants.get(auth.userId()).getRole()!=User.Role.TEACHER) throw new ApiException(403,"ACCESS_DENIED","선생님만 초대할 수 있습니다.");
        if(participants.get(student).getRole()!=User.Role.STUDENT) throw ApiException.missing();
        if(relationships.exists(auth.userId(),student)) throw new ApiException(409,"RELATION_EXISTS","이미 연결된 학생입니다.");
        sessions.rate("relationship-invite",auth.userId().toString(),30,60);
        return InvitationView.of(relationships.create(auth.userId(),student));
    }
    public PageResult<InvitationView> invitations(AuthPrincipal auth,String cursor,int limit) {
        return PageResult.of(relationships.invitations(auth.userId(),PageResult.cursor(cursor),PageResult.limit(limit)),limit,com.tutorservice.relationship.entity.Invitation::id,InvitationView::of);
    }
    public PageResult<RelationView> list(AuthPrincipal auth,String cursor,int limit) {
        return PageResult.of(relationships.relations(auth.userId(),PageResult.cursor(cursor),PageResult.limit(limit)),limit,com.tutorservice.relationship.entity.Relationship::id,RelationView::of);
    }
    @Transactional
    public RelationView accept(AuthPrincipal auth,UUID id) {
        var preview=relationships.invitation(id,false).filter(i->i.recipientId().equals(auth.userId())).orElseThrow(ApiException::missing);
        var participants=lock(auth,preview.senderId(),preview.recipientId());
        if(participants.get(preview.senderId()).getRole()!=User.Role.TEACHER || participants.get(preview.recipientId()).getRole()!=User.Role.STUDENT) throw ApiException.missing();
        var invitation=relationships.invitation(id,true).orElseThrow(ApiException::missing);
        if(invitation.consumedAt()!=null || !invitation.expiresAt().isAfter(Instant.now()) || relationships.exists(invitation.senderId(),invitation.recipientId())) throw new ApiException(409,"INVITATION_UNAVAILABLE","만료되었거나 이미 처리한 초대입니다.");
        return RelationView.of(relationships.accept(invitation));
    }
    @Transactional
    public void revoke(AuthPrincipal auth,UUID id) {
        var r=relationships.relation(id).filter(x->x.teacherId().equals(auth.userId())||x.studentId().equals(auth.userId())).orElseThrow(ApiException::missing);
        lock(auth,r.teacherId(),r.studentId()); relationships.revoke(id);
    }
}
