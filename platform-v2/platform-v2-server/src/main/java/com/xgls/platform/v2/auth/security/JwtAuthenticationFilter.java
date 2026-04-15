package com.xgls.platform.v2.auth.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xgls.platform.v2.api.v1.common.AjaxResult;
import com.xgls.platform.v2.api.v1.common.ErrorCode;
import com.xgls.platform.v2.auth.config.PlatformAuthProperties;
import com.xgls.platform.v2.auth.jwt.LegacyJwtService;
import com.xgls.platform.v2.auth.jwt.LegacyJwtService.LegacyJwtClaims;
import com.xgls.platform.v2.auth.redis.TokenRedisStore;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Validates {@code Authorization} header against Redis + Hutool JWT (same behaviour as legacy Shiro {@code JwtRealm}).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTHORIZATION_HEADER = "Authorization";

    private final LegacyJwtService jwtService;
    private final TokenRedisStore tokenRedisStore;
    private final PlatformAuthProperties authProperties;
    private final RequestMatcher publicEndpoints;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtAuthenticationFilter(LegacyJwtService jwtService, TokenRedisStore tokenRedisStore,
            PlatformAuthProperties authProperties) {
        this.jwtService = jwtService;
        this.tokenRedisStore = tokenRedisStore;
        this.authProperties = authProperties;
        this.publicEndpoints = new OrRequestMatcher(List.of(
                new AntPathRequestMatcher("/actuator/**"),
                new AntPathRequestMatcher("/api/v1/ping"),
                new AntPathRequestMatcher("/api/v2/ping"),
                new AntPathRequestMatcher("/auth/login", "POST"),
                new AntPathRequestMatcher("/api/v1/auth/login", "POST"),
                new AntPathRequestMatcher("/auth/logout", "POST"),
                new AntPathRequestMatcher("/api/v1/auth/logout", "POST"),
                new AntPathRequestMatcher("/auth/unauthorized", "GET"),
                new AntPathRequestMatcher("/api/v1/auth/unauthorized", "GET")));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return publicEndpoints.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String headerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (headerToken == null || headerToken.isBlank()) {
            writeUnauthorized(response);
            return;
        }

        String realToken = tokenRedisStore.getRealToken(headerToken);
        if (realToken == null) {
            writeUnauthorized(response);
            return;
        }

        LegacyJwtClaims claims = jwtService.parseAndValidate(realToken);
        if (claims == null) {
            writeUnauthorized(response);
            return;
        }

        long expSec = authProperties.getJwtExpireSeconds();
        if (claims.expired()) {
            String refreshed = jwtService.generateToken(claims.id(), claims.username(), claims.legacyType());
            tokenRedisStore.bind(headerToken, refreshed, expSec);
        } else {
            tokenRedisStore.refreshTtl(headerToken, realToken, expSec);
        }

        LoginPrincipal principal = new LoginPrincipal(
                claims.id(),
                claims.username(),
                claims.legacyType(),
                claims.legacyType() == 1 ? List.of("ADMIN") : List.of("USER"));

        var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(AjaxResult.error(ErrorCode.AUTH_FAILED)));
    }
}
