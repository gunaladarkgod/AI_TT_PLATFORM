package com.xgls.platform.v2.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "platform.auth")
public class PlatformAuthProperties {

    /** Same semantics as legacy sys.jwt-key */
    private String jwtSecret = "xgls!123#hk";

    /** Token TTL in seconds (legacy: 86400) */
    private long jwtExpireSeconds = 86400;

    /** Concat: MD5(plainPassword + passwordSaltSuffix) — legacy CodeMap.XGLS */
    private String passwordSaltSuffix = "xglszm";

    /** When true, login refuses until license module is wired (legacy always checked) */
    private boolean licenseRequired = false;

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public long getJwtExpireSeconds() {
        return jwtExpireSeconds;
    }

    public void setJwtExpireSeconds(long jwtExpireSeconds) {
        this.jwtExpireSeconds = jwtExpireSeconds;
    }

    public String getPasswordSaltSuffix() {
        return passwordSaltSuffix;
    }

    public void setPasswordSaltSuffix(String passwordSaltSuffix) {
        this.passwordSaltSuffix = passwordSaltSuffix;
    }

    public boolean isLicenseRequired() {
        return licenseRequired;
    }

    public void setLicenseRequired(boolean licenseRequired) {
        this.licenseRequired = licenseRequired;
    }
}
