package com.xgls.platform.v2.auth.security;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.xgls.platform.v2.auth.config.PlatformAuthProperties;
import com.xgls.platform.v2.auth.jwt.LegacyJwtService;
import com.xgls.platform.v2.auth.redis.TokenRedisStore;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    private static final String[] PUBLIC = {
            "/actuator/**",
            "/api/v1/ping",
            "/api/v2/ping",
            "/auth/login",
            "/api/v1/auth/login",
            "/auth/logout",
            "/api/v1/auth/logout",
            "/auth/unauthorized",
            "/api/v1/auth/unauthorized"
    };

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(LegacyJwtService jwtService, TokenRedisStore tokenRedisStore,
            PlatformAuthProperties authProperties) {
        return new JwtAuthenticationFilter(jwtService, tokenRedisStore, authProperties);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(c -> c.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOriginPatterns(List.of("*"));
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("*"));
        c.setExposedHeaders(List.of("*"));
        c.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", c);
        return source;
    }
}
