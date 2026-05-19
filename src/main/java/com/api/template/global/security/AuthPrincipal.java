package com.api.template.global.security;

import java.util.Collection;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
@RequiredArgsConstructor
public class AuthPrincipal implements UserDetails {

    private final Long id;
    private final String username;
    private final Collection<? extends GrantedAuthority> authorities;

    public static AuthPrincipal of(Long id, String username, Collection<String> roles) {
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(AuthPrincipal::toAuthority)
                .toList();
        return new AuthPrincipal(id, username, authorities);
    }

    public static AuthPrincipal authenticated(
            Long id,
            String username,
            Collection<? extends GrantedAuthority> authorities) {
        return new AuthPrincipal(id, username, authorities);
    }

    private static SimpleGrantedAuthority toAuthority(String role) {
        if (role.startsWith("ROLE_")) {
            return new SimpleGrantedAuthority(role);
        }
        return new SimpleGrantedAuthority("ROLE_" + role);
    }

    @Override
    public String getPassword() {
        return null;
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
