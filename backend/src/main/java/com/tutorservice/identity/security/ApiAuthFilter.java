package com.tutorservice.identity.security;

import com.tutorservice.common.web.*;
import com.tutorservice.identity.service.MemberService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

public class ApiAuthFilter extends OncePerRequestFilter {
    private final JwtService jwt; private final RefreshStore sessions; private final MemberService members; private final AuthProperties props;
    public ApiAuthFilter(JwtService jwt, RefreshStore sessions, MemberService members, AuthProperties props) {
        this.jwt=jwt; this.sessions=sessions; this.members=members; this.props=props;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException,IOException {
        response.setHeader("Cache-Control","no-store");
        if(!Set.of("GET","HEAD","OPTIONS").contains(request.getMethod()) && !props.allowedOrigins().contains(request.getHeader("Origin"))) {
            ApiErrors.write(response,403,"ORIGIN_REJECTED"); return;
        }
        if(!Set.of("GET","HEAD","OPTIONS").contains(request.getMethod())) {
            if(request.getContentLengthLong()>16384) { ApiErrors.write(response,413,"PAYLOAD_TOO_LARGE"); return; }
            byte[] body=request.getInputStream().readNBytes(16385);
            if(body.length>16384) { ApiErrors.write(response,413,"PAYLOAD_TOO_LARGE"); return; }
            request=new BodyRequest(request,body);
        }
        try {
            String header=request.getHeader("Authorization");
            if(header!=null) {
                if(!header.startsWith("Bearer ") || header.length()>4096) throw ApiException.unauthorized();
                var token=jwt.decode(header.substring(7));
                UUID userId=UUID.fromString(token.getSubject()); UUID family=UUID.fromString(token.getClaimAsString("sid"));
                long version=((Number)token.getClaim("ver")).longValue();
                var session=sessions.session(family); var user=members.active(userId);
                if(!session.userId().equals(userId) || session.version()!=version || user.getSessionVersion()!=version) throw ApiException.unauthorized();
                var principal=new AuthPrincipal(userId,family,version);
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,List.of(new SimpleGrantedAuthority("ROLE_"+user.getRole().name()))));
            }
        } catch(ApiException e) { ApiErrors.write(response,e.status,e.code); return; }
          catch(JwtException|IllegalArgumentException e) { ApiErrors.write(response,401,"UNAUTHENTICATED"); return; }
          catch(DataAccessException e) { ApiErrors.write(response,503,"DEPENDENCY_UNAVAILABLE"); return; }
        chain.doFilter(request,response);
    }
    private static final class BodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        BodyRequest(HttpServletRequest request,byte[] body) { super(request); this.body=body; }
        @Override public ServletInputStream getInputStream() {
            var input=new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public int read(byte[] b,int off,int len) { return input.read(b,off,len); }
                @Override public boolean isFinished() { return input.available()==0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("Synchronous JSON API"); }
            };
        }
        @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8)); }
    }
}
