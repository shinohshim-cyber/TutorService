package com.tutorservice.identity;

import com.tutorservice.identity.entity.User;
import com.tutorservice.identity.security.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;
import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    private AuthProperties props(String current,String previous) { return new AuthProperties(current,"new",previous,"old","cc".repeat(32),"tutor-service","tutor-web",true,List.of("https://example.test")); }
    private String token(String secret,String kid,String issuer,String audience,Instant expiry,boolean sid) throws Exception {
        var now=Instant.now();
        var claims=new JWTClaimsSet.Builder().subject(UUID.randomUUID().toString()).jwtID(UUID.randomUUID().toString()).issuer(issuer).audience(audience)
            .issueTime(Date.from(now.minusSeconds(60))).notBeforeTime(Date.from(now.minusSeconds(60))).expirationTime(Date.from(expiry)).claim("ver",0);
        if(sid) claims.claim("sid",UUID.randomUUID().toString());
        var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(kid).build(),claims.build());
        jwt.sign(new MACSigner(HexFormat.of().parseHex(secret))); return jwt.serialize();
    }
    @Test void issueAndVerify() {
        var jwt=new JwtService(props("aa".repeat(32),""));var u=new User("a@example.test","hash","학생",User.Role.STUDENT);var family=UUID.randomUUID();
        var decoded=jwt.decode(jwt.issue(u,family));assertEquals(u.getId().toString(),decoded.getSubject());assertEquals(family.toString(),decoded.getClaimAsString("sid"));
    }
    @Test void rotationAndClaimRejection() throws Exception {
        var jwt=new JwtService(props("aa".repeat(32),"bb".repeat(32)));var valid=Instant.now().plusSeconds(60);
        assertNotNull(jwt.decode(token("bb".repeat(32),"old","tutor-service","tutor-web",valid,true)));
        assertThrows(JwtException.class,()->jwt.decode(token("dd".repeat(32),"new","tutor-service","tutor-web",valid,true)));
        assertThrows(JwtException.class,()->jwt.decode(token("aa".repeat(32),"unknown","tutor-service","tutor-web",valid,true)));
        assertThrows(JwtException.class,()->jwt.decode(token("aa".repeat(32),"new","wrong","tutor-web",valid,true)));
        assertThrows(JwtException.class,()->jwt.decode(token("aa".repeat(32),"new","tutor-service","wrong",valid,true)));
        assertThrows(JwtException.class,()->jwt.decode(token("aa".repeat(32),"new","tutor-service","tutor-web",Instant.now().minusSeconds(30),true)));
        assertThrows(JwtException.class,()->jwt.decode(token("aa".repeat(32),"new","tutor-service","tutor-web",valid,false)));
        var removed=new JwtService(props("aa".repeat(32),""));
        assertThrows(JwtException.class,()->removed.decode(token("bb".repeat(32),"old","tutor-service","tutor-web",valid,true)));
    }
    @Test void rejectUnsignedAndWrongAlgorithm() {
        var jwt=new JwtService(props("aa".repeat(32),""));
        assertThrows(JwtException.class,()->jwt.decode(new PlainJWT(new JWTClaimsSet.Builder().subject("x").build()).serialize()));
        assertThrows(IllegalArgumentException.class,()->new JwtService(props("short","")));
    }
}
