package com.xgls.platform.v2.auth.redis;

import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Same key layout as legacy {@code RedisService}: {@code token:<headerValue>} → real JWT string;
 * {@code uid:<userId>} → Set of header keys for that user.
 */
@Component
public class TokenRedisStore {

    private static final String TOKEN_PREFIX = "token:";
    private static final String UID_PREFIX = "uid:";

    private final StringRedisTemplate redis;

    public TokenRedisStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String getRealToken(String headerToken) {
        return redis.opsForValue().get(TOKEN_PREFIX + headerToken);
    }

    public void bind(String headerToken, String realJwt, long expireSeconds) {
        if (realJwt == null) {
            return;
        }
        redis.opsForValue().set(TOKEN_PREFIX + headerToken, realJwt, expireSeconds, TimeUnit.SECONDS);
    }

    public void removeHeaderToken(String headerToken) {
        redis.delete(TOKEN_PREFIX + headerToken);
    }

    public void addUserSessionKey(long userId, String headerToken) {
        redis.opsForSet().add(UID_PREFIX + userId, headerToken);
    }

    public void removeUserSessionKey(long userId, String headerToken) {
        redis.opsForSet().remove(UID_PREFIX + userId, headerToken);
    }

    public void refreshTtl(String headerToken, String realJwt, long expireSeconds) {
        bind(headerToken, realJwt, expireSeconds);
    }
}
