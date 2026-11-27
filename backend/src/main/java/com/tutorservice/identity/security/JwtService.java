package com.tutorservice.identity.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.*;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.tutorservice.identity.entity.User;
import java.time.*;
import java.util.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final AuthProperties props;
    private final JwtEncoder encoder;
    private final NimbusJwtDecoder decoder;
    public JwtService(AuthProperties props) {
        this.props=props;
        var active=key(props.jwtKey(),props.jwtKid());
        var keys=new ArrayList<JWK>(); keys.add(active);
        if(props.previousKey()!=null && !props.previousKey().isBlank()) {
            if(props.jwtKid().equals(props.previousKid())) throw new IllegalArgumentException("JWT kid must be distinct");
            keys.add(key(props.previousKey(),props.previousKid()));
        }
        encoder=new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(active)));
        var processor=new DefaultJWTProcessor<SecurityContext>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.HS256,new ImmutableJWKSet<>(new JWKSet(keys))));
        decoder=new NimbusJwtDecoder(processor);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(Duration.ZERO), new JwtIssuerValidator(props.issuer()),
            jwt -> validClaims(jwt) ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"))));
    }
    private static OctetSequenceKey key(String hex, String kid) {
        if(hex==null || !hex.matches("[0-9a-fA-F]{64,128}") || hex.length()%2!=0 || kid==null || kid.isBlank()) throw new IllegalArgumentException("JWT key configuration is invalid");
        return new OctetSequenceKey.Builder(HexFormat.of().parseHex(hex)).keyID(kid).algorithm(JWSAlgorithm.HS256).build();
    }
    private boolean validClaims(Jwt jwt) {
        try {
            UUID.fromString(jwt.getSubject()); UUID.fromString(jwt.getId()); UUID.fromString(jwt.getClaimAsString("sid"));
            String kid=(String)jwt.getHeaders().get("kid");
            return (props.jwtKid().equals(kid)||(!props.previousKey().isBlank()&&props.previousKid().equals(kid)))
                && jwt.getAudience().contains(props.audience()) && jwt.getExpiresAt()!=null && jwt.getNotBefore()!=null
                && jwt.getIssuedAt()!=null && !jwt.getIssuedAt().isAfter(Instant.now().plusSeconds(1))
                && jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
                && Duration.between(jwt.getIssuedAt(),jwt.getExpiresAt()).getSeconds()<=900
                && ((Number)jwt.getClaim("ver")).longValue()>=0;
        } catch(RuntimeException e) { return false; }
    }
    public String issue(User user, UUID family) {
        var now=Instant.now();
        var claims=JwtClaimsSet.builder().issuer(props.issuer()).audience(List.of(props.audience())).subject(user.getId().toString())
            .id(UUID.randomUUID().toString()).issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(900))
            .claim("sid",family.toString()).claim("ver",user.getSessionVersion()).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).keyId(props.jwtKid()).build(),claims)).getTokenValue();
    }
    public Jwt decode(String token) { return decoder.decode(token); }
}
