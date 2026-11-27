package com.tutorservice.identity.security;

import com.tutorservice.common.web.ApiErrors;
import com.tutorservice.identity.service.MemberService;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.*;
import org.springframework.web.cors.*;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {
    @Bean PasswordEncoder passwords() { return new BCryptPasswordEncoder(12); }
    @Bean SecurityFilterChain security(HttpSecurity http, AuthProperties props, JwtService jwt, RefreshStore sessions, MemberService members) throws Exception {
        var csrf=CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookiePath("/api/v1");
        csrf.setCookieCustomizer(c -> c.secure(props.secureCookie()).sameSite("Strict"));
        var cors=new CorsConfiguration(); cors.setAllowedOrigins(props.allowedOrigins()); cors.setAllowCredentials(true);
        cors.setAllowedMethods(List.of("GET","POST","PATCH","DELETE","OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization","Content-Type","X-XSRF-TOKEN"));
        var source=new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/**",cors);
        return http.httpBasic(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .cors(c -> c.configurationSource(source))
            .csrf(c -> c.csrfTokenRepository(csrf).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .addFilterBefore(new ApiAuthFilter(jwt,sessions,members,props),org.springframework.web.filter.CorsFilter.class)
            .authorizeHttpRequests(a -> a.requestMatchers("/actuator/health","/actuator/health/**","/api/v1/system/status","/api/v1/auth/**").permitAll()
                .requestMatchers("/api/v1/relationships/invitations").hasAnyRole("STUDENT","TEACHER")
                .requestMatchers("/api/v1/users/**","/api/v1/relationships/**").authenticated().anyRequest().denyAll())
            .exceptionHandling(e -> e.authenticationEntryPoint((q,s,x)->ApiErrors.write(s,401,"UNAUTHENTICATED"))
                .accessDeniedHandler((q,s,x)->ApiErrors.write(s,403,"ACCESS_DENIED")))
            .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
            .build();
    }
}
