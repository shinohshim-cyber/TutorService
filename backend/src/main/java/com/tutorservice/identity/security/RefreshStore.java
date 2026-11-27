package com.tutorservice.identity.security;

import com.tutorservice.common.web.ApiException;
import com.tutorservice.identity.entity.User;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class RefreshStore {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(RefreshStore.class);
    public static final long LIFETIME=14*24*60*60;
    private final StringRedisTemplate redis;
    private final byte[] hmacKey;
    private final SecureRandom random=new SecureRandom();
    private static final DefaultRedisScript<Long> CREATE=new DefaultRedisScript<>("""
        if redis.call('EXISTS',KEYS[1])==1 then return 0 end
        redis.call('HSET',KEYS[1],'uid',ARGV[1],'ver',ARGV[2],'exp',ARGV[3],'revoked','0','t:'..ARGV[4],'ACTIVE')
        redis.call('EXPIREAT',KEYS[1],ARGV[3]); return 1
        """,Long.class);
    private static final DefaultRedisScript<Long> ROTATE=new DefaultRedisScript<>("""
        if redis.call('HGET',KEYS[1],'revoked')~='0' then return 0 end
        if tonumber(redis.call('HGET',KEYS[1],'exp') or '0')<=tonumber(ARGV[3]) then return 0 end
        local state=redis.call('HGET',KEYS[1],'t:'..ARGV[1])
        if state=='USED' then redis.call('HSET',KEYS[1],'revoked','1'); return -1 end
        if state~='ACTIVE' then return 0 end
        redis.call('HSET',KEYS[1],'t:'..ARGV[1],'USED','t:'..ARGV[2],'ACTIVE'); return 1
        """,Long.class);
    private static final DefaultRedisScript<Long> REVOKE=new DefaultRedisScript<>("""
        if redis.call('HEXISTS',KEYS[1],'t:'..ARGV[1])==1 then redis.call('HSET',KEYS[1],'revoked','1'); return 1 end
        return 0
        """,Long.class);
    public record Token(UUID family, String value) {}
    public record Session(UUID userId, long version, long expires) {}
    public RefreshStore(StringRedisTemplate redis, AuthProperties props) {
        this.redis=redis;
        if(props.refreshHmacKey()==null || !props.refreshHmacKey().matches("[0-9a-fA-F]{64}")) throw new IllegalArgumentException("Refresh HMAC key configuration is invalid");
        hmacKey=HexFormat.of().parseHex(props.refreshHmacKey());
    }
    public String digest(String value) {
        try { var mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(hmacKey,"HmacSHA256")); return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8))); }
        catch(GeneralSecurityException e) { throw new IllegalStateException("HMAC unavailable"); }
    }
    public static UUID family(String token) {
        if(token==null || !token.matches("[0-9a-f-]{36}\\.[0-9a-f]{64}")) throw ApiException.unauthorized();
        try { return UUID.fromString(token.substring(0,36)); } catch(IllegalArgumentException e) { throw ApiException.unauthorized(); }
    }
    private static String key(UUID id) { return "tutor:auth:family:"+id; }
    private String randomToken(UUID id) { byte[] bytes=new byte[32]; random.nextBytes(bytes); return id+"."+HexFormat.of().formatHex(bytes); }
    public Token create(User user) {
        UUID id=UUID.randomUUID(); String raw=randomToken(id);
        try {
            var result=redis.execute(CREATE,List.of(key(id)),user.getId().toString(),Long.toString(user.getSessionVersion()),Long.toString(Instant.now().getEpochSecond()+LIFETIME),digest(raw));
            if(!Long.valueOf(1).equals(result)) throw ApiException.unavailable();
            return new Token(id,raw);
        } catch(DataAccessException e) { throw ApiException.unavailable(); }
    }
    public Session session(UUID family) {
        try {
            Map<Object,Object> data=redis.opsForHash().entries(key(family));
            if(!"0".equals(data.get("revoked"))) throw ApiException.unauthorized();
            long exp=Long.parseLong((String)data.get("exp"));
            if(exp<=Instant.now().getEpochSecond()) throw ApiException.unauthorized();
            return new Session(UUID.fromString((String)data.get("uid")),Long.parseLong((String)data.get("ver")),exp);
        } catch(DataAccessException e) { throw ApiException.unavailable(); }
    }
    public Token rotate(String old) {
        UUID id=family(old); String raw=randomToken(id);
        try {
            Long result=redis.execute(ROTATE,List.of(key(id)),digest(old),digest(raw),Long.toString(Instant.now().getEpochSecond()));
            if(Long.valueOf(-1).equals(result)) LOG.warn("auth_refresh_replay: session family revoked");
            if(!Long.valueOf(1).equals(result)) throw ApiException.unauthorized();
            return new Token(id,raw);
        } catch(DataAccessException e) { throw ApiException.unavailable(); }
    }
    public void revoke(String token) {
        UUID id=family(token);
        try { redis.execute(REVOKE,List.of(key(id)),digest(token)); } catch(DataAccessException e) { throw ApiException.unavailable(); }
    }
    public void rate(String scope, String value, int maximum, int seconds) {
        var script=new DefaultRedisScript<Long>("local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]) end; return n",Long.class);
        try {
            Long count=redis.execute(script,List.of("tutor:auth:rate:"+scope+":"+digest(value)),Integer.toString(seconds));
            if(count==null) throw ApiException.unavailable();
            if(count>maximum) throw new ApiException(429,"RATE_LIMITED","잠시 후 다시 시도해 주세요.");
        } catch(DataAccessException e) { throw ApiException.unavailable(); }
    }
}
