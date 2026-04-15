package com.xgls.platform.v2.auth.jwt;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.xgls.platform.v2.auth.config.PlatformAuthProperties;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTPayload;
import cn.hutool.json.JSONObject;

/**
 * Hutool JWT HS256 — token shape compatible with legacy {@code JwtUtils} (claims: id, username, type).
 */
@Service
public class LegacyJwtService {

    private final PlatformAuthProperties properties;

    public LegacyJwtService(PlatformAuthProperties properties) {
        this.properties = properties;
    }

    public byte[] signingKey() {
        return properties.getJwtSecret().getBytes(StandardCharsets.UTF_8);
    }

    public long expireSeconds() {
        return properties.getJwtExpireSeconds();
    }

    public String generateToken(long userId, String username, int legacyUserType) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", userId);
        map.put("username", username);
        map.put("type", legacyUserType);
        JWT jwt = JWT.create();
        map.forEach(jwt::setPayload);
        jwt.setKey(signingKey());
        long now = System.currentTimeMillis();
        jwt.setExpiresAt(new Date(now + expireSeconds() * 1000));
        return jwt.sign();
    }

    public boolean verifySignature(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            return JWT.of(token).setKey(signingKey()).verify();
        } catch (Exception e) {
            return false;
        }
    }

    public LegacyJwtClaims parseAndValidate(String token) {
        if (!verifySignature(token)) {
            return null;
        }
        JWT jwt = JWT.of(token);
        JSONObject jo = jwt.getPayload().getClaimsJson();
        if (jo == null) {
            return null;
        }
        Long id = jo.getLong("id");
        String username = jo.getStr("username");
        Integer type = jo.getInt("type");
        Date exp = jo.getDate(JWTPayload.EXPIRES_AT);
        if (id == null || username == null || type == null) {
            return null;
        }
        boolean expired = exp != null && !exp.after(new Date());
        return new LegacyJwtClaims(id, username, type, expired);
    }

    public record LegacyJwtClaims(long id, String username, int legacyType, boolean expired) {}
}
