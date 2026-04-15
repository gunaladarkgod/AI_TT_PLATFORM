package com.xgls.platform.v2.api.v1.auth;

import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xgls.platform.v2.api.v1.common.AjaxResult;
import com.xgls.platform.v2.api.v1.common.ErrorCode;
import com.xgls.platform.v2.auth.config.PlatformAuthProperties;
import com.xgls.platform.v2.auth.crypto.LegacyPasswordHasher;
import com.xgls.platform.v2.auth.jwt.LegacyJwtService;
import com.xgls.platform.v2.auth.redis.TokenRedisStore;
import com.xgls.platform.v2.auth.security.JwtAuthenticationFilter;
import com.xgls.platform.v2.auth.user.UserAccountRepository;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Legacy-compatible auth endpoints. Both {@code /auth/*} and {@code /api/v1/auth/*} are mapped.
 */
@RestController
public class AuthController {

    private final UserAccountRepository userAccountRepository;
    private final LegacyPasswordHasher passwordHasher;
    private final LegacyJwtService jwtService;
    private final TokenRedisStore tokenRedisStore;
    private final PlatformAuthProperties authProperties;

    public AuthController(UserAccountRepository userAccountRepository, LegacyPasswordHasher passwordHasher,
            LegacyJwtService jwtService, TokenRedisStore tokenRedisStore, PlatformAuthProperties authProperties) {
        this.userAccountRepository = userAccountRepository;
        this.passwordHasher = passwordHasher;
        this.jwtService = jwtService;
        this.tokenRedisStore = tokenRedisStore;
        this.authProperties = authProperties;
    }

    @PostMapping({"/auth/login", "/api/v1/auth/login"})
    public AjaxResult login(@RequestParam("username") String username, @RequestParam("pmd") String pmd) {
        if (authProperties.isLicenseRequired()) {
            return AjaxResult.error("License 未接入：请在 platform.auth.license-required=false 或接入许可证模块");
        }
        if (!StringUtils.hasText(username) || !StringUtils.hasText(pmd)) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }

        var userOpt = userAccountRepository.findByUsername(username.trim());
        if (userOpt.isEmpty()) {
            return AjaxResult.error(ErrorCode.ACCOUNT_PWD_WRONG);
        }
        var user = userOpt.get();
        if (!user.active()) {
            return AjaxResult.error(ErrorCode.ACCOUNT_LOCKED);
        }
        if (!passwordHasher.matches(pmd, user.passwordHash())) {
            return AjaxResult.error(ErrorCode.ACCOUNT_PWD_WRONG);
        }

        int legacyType = user.legacyUserType();
        String token = jwtService.generateToken(user.id(), user.username(), legacyType);
        long expire = jwtService.expireSeconds();
        tokenRedisStore.bind(token, token, expire);
        tokenRedisStore.addUserSessionKey(user.id(), token);

        return AjaxResult.success(token);
    }

    @PostMapping({"/auth/logout", "/api/v1/auth/logout"})
    public AjaxResult logout(HttpServletRequest request) {
        String header = request.getHeader(JwtAuthenticationFilter.AUTHORIZATION_HEADER);
        if (!StringUtils.hasText(header)) {
            return AjaxResult.success();
        }
        String realToken = tokenRedisStore.getRealToken(header);
        if (realToken != null) {
            var claims = jwtService.parseAndValidate(realToken);
            tokenRedisStore.removeHeaderToken(header);
            if (claims != null) {
                tokenRedisStore.removeUserSessionKey(claims.id(), header);
            }
        } else {
            tokenRedisStore.removeHeaderToken(header);
        }
        return AjaxResult.success();
    }

    @GetMapping({"/auth/unauthorized", "/api/v1/auth/unauthorized"})
    public AjaxResult unauthorized() {
        return AjaxResult.error(ErrorCode.AUTH_FAILED);
    }
}
