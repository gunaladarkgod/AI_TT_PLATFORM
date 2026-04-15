package com.xgls.platform.v2.auth.crypto;

import org.springframework.stereotype.Component;

import com.xgls.platform.v2.auth.config.PlatformAuthProperties;

import cn.hutool.crypto.SecureUtil;

@Component
public class LegacyPasswordHasher {

    private final PlatformAuthProperties properties;

    public LegacyPasswordHasher(PlatformAuthProperties properties) {
        this.properties = properties;
    }

    /** Legacy: MD5(plainPassword + saltSuffix), compare to usr_user.password_hash */
    public String hash(String plainPassword) {
        return SecureUtil.md5(plainPassword + properties.getPasswordSaltSuffix());
    }

    public boolean matches(String plainPassword, String storedHash) {
        if (plainPassword == null || storedHash == null) {
            return false;
        }
        return storedHash.equalsIgnoreCase(hash(plainPassword));
    }
}
