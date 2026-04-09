package com.xgls.web.service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.xgls.web.service.RedisService;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class RedisService {
    private static String USER_PE = "user:";
    private static String LOCK_SUF = ":lockTime";
    private static String FAIL_SUF = ":failCount";
    private static String TOKEN_PE = "token:";
    private static String UID_PE = "uid:";

    /**
     * 失败次数记录有效期5分钟
     */
    private static long FAILTIME = 60 * 5;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public long getUserLoginTimeLock(String username) {
        String key = USER_PE + username + LOCK_SUF;
        Long val = redisTemplate.opsForValue().getOperations().getExpire(key);
        return val == null ? -1 : val;
    }

    public void setUserLoginTimeLock(String username, Long lockTime) {
        String key = USER_PE + username + LOCK_SUF;
        redisTemplate.opsForValue().set(key, 1, lockTime, TimeUnit.SECONDS);
    }

    public void setFailCount(String username) {
        String key = USER_PE + username + FAIL_SUF;
        Object object = redisTemplate.opsForValue().get(key);
        int cnt = object == null ? -1 : (int) object;
        if (cnt < 0) {
            redisTemplate.opsForValue().set(key, 1, FAILTIME, TimeUnit.SECONDS);
        } else {
            redisTemplate.opsForValue().increment(key, 1);
        }
    }

    public int getUserFailCount(String username) {
        String key = USER_PE + username + FAIL_SUF;
        // 从redis中获取当前用户已失败次数
        Object object = redisTemplate.opsForValue().get(key);
        if (object != null) {
            return (int) object;
        } else {
            return -1;
        }
    }

    public void delFailCount(String username) {
        String key = USER_PE + username + FAIL_SUF;
        redisTemplate.delete(key);
    }

    public String getJwtToken(String keyToken) {
        String key = TOKEN_PE + keyToken;
        Object object = redisTemplate.opsForValue().get(key);
        if (object != null) {
            return (String) object.toString();
        } else {
            return null;
        }
    }

    public void setJwtToken(String keyToken, String realToken, Long expireTime) {
        if (realToken == null) {
            return;
        }
        String key = TOKEN_PE + keyToken;
        redisTemplate.opsForValue().set(key, realToken, expireTime, TimeUnit.SECONDS);
    }

    public void updateJwtToken(String keyToken, String realToken) {
        if (realToken == null) {
            return;
        }
        String key = TOKEN_PE + keyToken;
        redisTemplate.opsForValue().setIfPresent(key, realToken);
    }

    public void removeJwtToken(String keyToken) {
        String key = TOKEN_PE + keyToken;
        redisTemplate.delete(key);
    }

    public void setUserJwt(Long userId, String keyToken) {
        String key = UID_PE + userId;
        redisTemplate.opsForSet().add(key, keyToken);
    }

    public Set<Object> getUserJwt(Long userId) {
        String key = UID_PE + userId;
        return redisTemplate.opsForSet().members(key);
    }

    public void removeUserJwt(Long userId, String keyToken) {
        String key = UID_PE + userId;
        redisTemplate.opsForSet().remove(key, keyToken);
    }

    public void delUserAllJwt(Long userId) {
        String key = UID_PE + userId;
        redisTemplate.delete(key);
    }

}
