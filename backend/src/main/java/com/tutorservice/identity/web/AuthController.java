package com.tutorservice.identity.web;

import com.tutorservice.identity.dto.IdentityDtos.*;
import com.tutorservice.identity.security.*;
import com.tutorservice.identity.service.MemberService;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final MemberService members; private final AuthProperties props;
    public AuthController(MemberService members, AuthProperties props) { this.members=members; this.props=props; }
    @GetMapping("/auth/csrf") public Map<String,String> csrf(CsrfToken token) { return Map.of("token",token.getToken(),"headerName",token.getHeaderName()); }
    @PostMapping("/auth/signup") @ResponseStatus(HttpStatus.CREATED)
    public Profile signup(@Valid @RequestBody Signup input, HttpServletRequest request) { return members.signup(input,request.getRemoteAddr()); }
    @PostMapping("/auth/login") public TokenResponse login(@Valid @RequestBody Login input, HttpServletRequest request, HttpServletResponse response) {
        var result=members.login(input,request.getRemoteAddr()); cookie(response,result.refresh().value(),RefreshStore.LIFETIME); return result.response();
    }
    @PostMapping("/auth/refresh") public TokenResponse refresh(@CookieValue(name="TUTOR_REFRESH",required=false) String raw, HttpServletResponse response) {
        var result=members.refresh(raw); cookie(response,result.refresh().value(),RefreshStore.LIFETIME); return result.response();
    }
    @PostMapping("/auth/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CookieValue(name="TUTOR_REFRESH",required=false) String raw, HttpServletResponse response) { members.logout(raw); cookie(response,"",0); }
    @GetMapping("/users/me") public Profile profile(@AuthenticationPrincipal AuthPrincipal auth) { return members.profile(auth); }
    @PatchMapping("/users/me") public Profile rename(@AuthenticationPrincipal AuthPrincipal auth, @Valid @RequestBody Rename input) { return members.rename(auth,input); }
    @DeleteMapping("/users/me") @ResponseStatus(HttpStatus.ACCEPTED)
    public Deletion delete(@AuthenticationPrincipal AuthPrincipal auth,@Valid @RequestBody DeleteAccount input,HttpServletResponse response) {
        var result=members.delete(auth,input); cookie(response,"",0); return result;
    }
    private void cookie(HttpServletResponse response,String value,long maxAge) {
        response.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from("TUTOR_REFRESH",value).httpOnly(true).secure(props.secureCookie()).sameSite("Strict")
            .path("/api/v1/auth").maxAge(Duration.ofSeconds(maxAge)).build().toString());
    }
}
