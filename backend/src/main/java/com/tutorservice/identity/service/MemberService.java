package com.tutorservice.identity.service;

import com.tutorservice.common.web.ApiException;
import com.tutorservice.identity.dto.IdentityDtos.*;
import com.tutorservice.identity.entity.User;
import com.tutorservice.identity.repository.UserRepository;
import com.tutorservice.identity.repository.IdentityCommandRepository;
import com.tutorservice.identity.security.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {
    private final UserRepository users;
    private final IdentityCommandRepository commands;
    private final PasswordEncoder passwords;
    private final RefreshStore sessions;
    private final JwtService jwt;
    private final String dummyHash;
    public record AuthResult(TokenResponse response, RefreshStore.Token refresh) {}
    public MemberService(UserRepository users, IdentityCommandRepository commands, PasswordEncoder passwords, RefreshStore sessions, JwtService jwt) {
        this.users=users; this.commands=commands; this.passwords=passwords; this.sessions=sessions; this.jwt=jwt;
        dummyHash=passwords.encode(UUID.randomUUID().toString());
    }
    public static String email(String value) { return value.strip().toLowerCase(Locale.ROOT); }
    public static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException("SHA256 unavailable"); }
    }
    @Transactional
    public Profile signup(Signup input, String ip) {
        sessions.rate("signup-ip",ip,30,3600);
        PasswordPolicy.check(input.password(),true);
        String normalized=email(input.email());
        User.Role role=input.requestedRole()==null?User.Role.STUDENT:input.requestedRole();
        if(role==User.Role.TEACHER) {
            if(input.teacherInvitationToken()==null || !input.teacherInvitationToken().matches("[0-9a-f]{64}")) throw new ApiException(403,"TEACHER_VERIFICATION_REQUIRED","검증된 선생님 가입 초대가 필요합니다.");
            if(!commands.consumeTeacherInvitation(normalized,sha256(input.teacherInvitationToken()))) throw new ApiException(403,"TEACHER_VERIFICATION_REQUIRED","검증된 선생님 가입 초대가 필요합니다.");
        } else if(input.teacherInvitationToken()!=null && !input.teacherInvitationToken().isBlank()) throw ApiException.invalid();
        return Profile.of(users.saveAndFlush(new User(normalized,passwords.encode(input.password()),input.name().strip(),role)));
    }
    @Transactional
    public AuthResult login(Login input, String ip) {
        PasswordPolicy.check(input.password(),false);
        String normalized=email(input.email());
        sessions.rate("login-ip",ip,100,60); sessions.rate("login-email",normalized,10,60);
        var user=users.lockByEmail(normalized).orElse(null);
        boolean matches=passwords.matches(input.password(),user==null?dummyHash:user.getPasswordHash());
        if(user==null || !matches || !user.active()) throw ApiException.unauthorized();
        return result(user,sessions.create(user));
    }
    @Transactional
    public AuthResult refresh(String raw) {
        var family=RefreshStore.family(raw); var session=sessions.session(family);
        var user=lock(session.userId());
        if(user.getSessionVersion()!=session.version()) throw ApiException.unauthorized();
        return result(user,sessions.rotate(raw));
    }
    private AuthResult result(User user, RefreshStore.Token refresh) {
        return new AuthResult(new TokenResponse(jwt.issue(user,refresh.family()),900,Profile.of(user)),refresh);
    }
    public User active(UUID id) { return users.findById(id).filter(User::active).orElseThrow(ApiException::unauthorized); }
    public User lock(UUID id) { return users.lockUsers(List.of(id)).stream().filter(User::active).findFirst().orElseThrow(ApiException::unauthorized); }
    public Profile profile(AuthPrincipal auth) { return Profile.of(active(auth.userId())); }
    private User current(AuthPrincipal auth) {
        var u=lock(auth.userId()); if(u.getSessionVersion()!=auth.version()) throw ApiException.unauthorized();
        var s=sessions.session(auth.family()); if(!s.userId().equals(u.getId()) || s.version()!=auth.version()) throw ApiException.unauthorized(); return u;
    }
    @Transactional
    public Profile rename(AuthPrincipal auth, Rename input) { var user=current(auth); user.rename(input.name().strip()); return Profile.of(user); }
    @Transactional
    public Deletion delete(AuthPrincipal auth, DeleteAccount input) {
        PasswordPolicy.check(input.password(),false); var user=current(auth);
        sessions.rate("delete-user",user.getId().toString(),5,60);
        if(!passwords.matches(input.password(),user.getPasswordHash())) throw ApiException.unauthorized();
        UUID deletion=commands.recordDeletion(user.getId());
        user.delete();
        return new Deletion(deletion);
    }
    public void logout(String raw) { if(raw!=null&&!raw.isBlank()) sessions.revoke(raw); }
}
