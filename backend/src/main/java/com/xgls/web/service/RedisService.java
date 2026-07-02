package com.xgls.web.service;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class RedisService {
    private static final String USER_PE = "user:";
    private static final String LOCK_SUF = ":lockTime";
    private static final String FAIL_SUF = ":failCount";
    private static final String TOKEN_PE = "token:";
    private static final String UID_PE = "uid:";

    /**
     * Failed login count TTL: 5 minutes.
     */
    private static final long FAILTIME = 60 * 5;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * Local development fallback. Enable with APP_REDIS_FALLBACK_MEMORY=true when
     * Redis is not installed. Tokens are kept only in JVM memory and disappear
     * after backend restart.
     */
    @Value("${app.redis.fallback-memory:false}")
    private boolean fallbackMemory;

    private final Map<String, MemoryValue> memoryValues = new ConcurrentHashMap<>();
    private final Map<String, Set<Object>> memorySets = new ConcurrentHashMap<>();

    private record MemoryValue(Object value, long expireAtMillis) {
    }

    private long expireAt(long seconds) {
        return seconds <= 0 ? 0 : System.currentTimeMillis() + seconds * 1000;
    }

    private Object memoryGet(String key) {
        MemoryValue item = memoryValues.get(key);
        if (item == null) {
            return null;
        }
        if (item.expireAtMillis() > 0 && item.expireAtMillis() <= System.currentTimeMillis()) {
            memoryValues.remove(key);
            return null;
        }
        return item.value();
    }

    private long memoryGetExpire(String key) {
        MemoryValue item = memoryValues.get(key);
        if (item == null || item.expireAtMillis() <= 0) {
            return -1;
        }
        long ttl = (item.expireAtMillis() - System.currentTimeMillis()) / 1000;
        if (ttl <= 0) {
            memoryValues.remove(key);
            return -1;
        }
        return ttl;
    }

    private void warnFallback(String operation, RuntimeException e) {
        if (!fallbackMemory) {
            throw e;
        }
        log.warn("Redis unavailable, using in-memory fallback for {}: {}", operation, e.getMessage());
    }

    public long getUserLoginTimeLock(String username) {
        String key = USER_PE + username + LOCK_SUF;
        try {
            Long val = redisTemplate.opsForValue().getOperations().getExpire(key);
            return val == null ? -1 : val;
        } catch (RuntimeException e) {
            warnFallback("getExpire", e);
            return memoryGetExpire(key);
        }
    }

    public void setUserLoginTimeLock(String username, Long lockTime) {
        String key = USER_PE + username + LOCK_SUF;
        try {
            redisTemplate.opsForValue().set(key, 1, lockTime, TimeUnit.SECONDS);
        } catch (RuntimeException e) {
            warnFallback("setUserLoginTimeLock", e);
            memoryValues.put(key, new MemoryValue(1, expireAt(lockTime)));
        }
    }

    public void setFailCount(String username) {
        String key = USER_PE + username + FAIL_SUF;
        try {
            Object object = redisTemplate.opsForValue().get(key);
            int cnt = object == null ? -1 : (int) object;
            if (cnt < 0) {
                redisTemplate.opsForValue().set(key, 1, FAILTIME, TimeUnit.SECONDS);
            } else {
                redisTemplate.opsForValue().increment(key, 1);
            }
        } catch (RuntimeException e) {
            warnFallback("setFailCount", e);
            Object object = memoryGet(key);
            int cnt = object == null ? 0 : Integer.parseInt(object.toString());
            memoryValues.put(key, new MemoryValue(cnt + 1, expireAt(FAILTIME)));
        }
    }

    public int getUserFailCount(String username) {
        String key = USER_PE + username + FAIL_SUF;
        try {
            Object object = redisTemplate.opsForValue().get(key);
            return object == null ? -1 : (int) object;
        } catch (RuntimeException e) {
            warnFallback("getUserFailCount", e);
            Object object = memoryGet(key);
            return object == null ? -1 : Integer.parseInt(object.toString());
        }
    }

    public void delFailCount(String username) {
        String key = USER_PE + username + FAIL_SUF;
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException e) {
            warnFallback("delFailCount", e);
            memoryValues.remove(key);
        }
    }

    public String getJwtToken(String keyToken) {
        String key = TOKEN_PE + keyToken;
        try {
            Object object = redisTemplate.opsForValue().get(key);
            return object == null ? null : object.toString();
        } catch (RuntimeException e) {
            warnFallback("getJwtToken", e);
            Object object = memoryGet(key);
            return object == null ? null : object.toString();
        }
    }

    public void setJwtToken(String keyToken, String realToken, Long expireTime) {
        if (realToken == null) {
            return;
        }
        String key = TOKEN_PE + keyToken;
        try {
            redisTemplate.opsForValue().set(key, realToken, expireTime, TimeUnit.SECONDS);
        } catch (RuntimeException e) {
            warnFallback("setJwtToken", e);
            memoryValues.put(key, new MemoryValue(realToken, expireAt(expireTime)));
        }
    }

    public void updateJwtToken(String keyToken, String realToken) {
        if (realToken == null) {
            return;
        }
        String key = TOKEN_PE + keyToken;
        try {
            redisTemplate.opsForValue().setIfPresent(key, realToken);
        } catch (RuntimeException e) {
            warnFallback("updateJwtToken", e);
            if (memoryGet(key) != null) {
                memoryValues.put(key, new MemoryValue(realToken, 0));
            }
        }
    }

    public void removeJwtToken(String keyToken) {
        String key = TOKEN_PE + keyToken;
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException e) {
            warnFallback("removeJwtToken", e);
            memoryValues.remove(key);
        }
    }

    public void setUserJwt(Long userId, String keyToken) {
        String key = UID_PE + userId;
        try {
            redisTemplate.opsForSet().add(key, keyToken);
        } catch (RuntimeException e) {
            warnFallback("setUserJwt", e);
            memorySets.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(keyToken);
        }
    }

    public Set<Object> getUserJwt(Long userId) {
        String key = UID_PE + userId;
        try {
            return redisTemplate.opsForSet().members(key);
        } catch (RuntimeException e) {
            warnFallback("getUserJwt", e);
            return memorySets.getOrDefault(key, Collections.emptySet()).stream().collect(Collectors.toSet());
        }
    }

    public void removeUserJwt(Long userId, String keyToken) {
        String key = UID_PE + userId;
        try {
            redisTemplate.opsForSet().remove(key, keyToken);
        } catch (RuntimeException e) {
            warnFallback("removeUserJwt", e);
            Set<Object> set = memorySets.get(key);
            if (set != null) {
                set.remove(keyToken);
            }
        }
    }

    public void delUserAllJwt(Long userId) {
        String key = UID_PE + userId;
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException e) {
            warnFallback("delUserAllJwt", e);
            memorySets.remove(key);
        }
    }
}
