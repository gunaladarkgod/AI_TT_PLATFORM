package com.xgls.platform.v2.auth.security;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Minimal {@link UserDetails} for JWT-backed sessions (password unused).
 */
public class LoginPrincipal implements UserDetails, Serializable {

    private final long userId;
    private final String username;
    private final int legacyType;
    private final List<GrantedAuthority> authorities;

    public LoginPrincipal(long userId, String username, int legacyType, Collection<String> roleCodes) {
        this.userId = userId;
        this.username = username;
        this.legacyType = legacyType;
        this.authorities = roleCodes.stream()
                .map(c -> new SimpleGrantedAuthority("ROLE_" + c.toUpperCase()))
                .collect(Collectors.toList());
    }

    public long getUserId() {
        return userId;
    }

    public int getLegacyType() {
        return legacyType;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
