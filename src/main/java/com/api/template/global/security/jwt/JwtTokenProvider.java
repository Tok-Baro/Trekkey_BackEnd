package com.api.template.global.security.jwt;

import com.api.template.global.security.AuthPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JwtTokenProvider implements InitializingBean {

    private static final String AUTHORITIES_KEY = "auth";
    private static final String ID_KEY = "id";

    private final JwtProperties jwtProperties;
    private SecretKey key;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @Override
    public void afterPropertiesSet() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.getSecretKey());
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public TokenDto createTokens(Authentication authentication) {
        String accessToken = createAccessToken(authentication);
        String refreshToken = createRefreshToken(authentication);
        return TokenDto.bearer(accessToken, refreshToken);
    }

    public String createAccessToken(Authentication authentication) {
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        Date validity = new Date(System.currentTimeMillis() + jwtProperties.getAccessExpiration() * 1000);

        return Jwts.builder()
                .subject(authentication.getName())
                .claim(ID_KEY, principal.getId())
                .claim(AUTHORITIES_KEY, authorities)
                .expiration(validity)
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    public String createRefreshToken(Authentication authentication) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        Date validity = new Date(System.currentTimeMillis() + jwtProperties.getRefreshExpiration() * 1000);

        return Jwts.builder()
                .subject(authentication.getName())
                .claim(ID_KEY, principal.getId())
                .expiration(validity)
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);

        Collection<? extends GrantedAuthority> authorities =
                Arrays.stream(claims.get(AUTHORITIES_KEY, String.class).split(","))
                        .filter(authority -> !authority.isBlank())
                        .map(SimpleGrantedAuthority::new)
                        .toList();

        Long id = claims.get(ID_KEY, Long.class);
        String username = claims.getSubject();
        AuthPrincipal principal = AuthPrincipal.authenticated(id, username, authorities);

        return new UsernamePasswordAuthenticationToken(principal, token, authorities);
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (io.jsonwebtoken.security.SignatureException | MalformedJwtException e) {
            log.warn("Invalid JWT signature.");
        } catch (ExpiredJwtException e) {
            log.warn("Expired JWT token.");
        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT token.");
        } catch (IllegalArgumentException e) {
            log.warn("JWT token is empty or invalid.");
        }
        return false;
    }

    public void validateTokenOrThrow(String token) throws JwtException {
        parseClaims(token);
    }

    public Long getExpiration(String token) {
        Date expiration = parseClaims(token).getExpiration();
        return expiration.getTime() - System.currentTimeMillis();
    }

    public Long getUserIdFromToken(String token) {
        return parseClaims(token).get(ID_KEY, Long.class);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
