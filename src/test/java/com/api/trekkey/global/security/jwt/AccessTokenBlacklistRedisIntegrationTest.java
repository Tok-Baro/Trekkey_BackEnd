package com.api.trekkey.global.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.api.trekkey.domain.audit.support.AdminAuditLogger;
import com.api.trekkey.domain.auth.repository.RefreshTokenRepository;
import com.api.trekkey.domain.auth.service.AuthServiceImpl;
import com.api.trekkey.domain.invitation.repository.AdminInvitationRepository;
import com.api.trekkey.domain.organization.repository.OrganizationRepository;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.security.AuthPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Run against a disposable Redis, never a shared/production instance. */
@EnabledIfEnvironmentVariable(named = "REDIS_INTEGRATION_PORT", matches = "\\d+")
class AccessTokenBlacklistRedisIntegrationTest {
    @Test
    void 로그아웃한_토큰은_다른_인스턴스에서도_차단되고_만료되면_레디스에서_삭제된다() throws Exception {
        LettuceConnectionFactory connection = new LettuceConnectionFactory("127.0.0.1",
                Integer.parseInt(System.getenv("REDIS_INTEGRATION_PORT")));
        connection.afterPropertiesSet();
        connection.start();
        try {
            StringRedisTemplate redis = new StringRedisTemplate(connection);
            JwtProperties properties = new JwtProperties();
            properties.setSecretKey(Base64.getEncoder().encodeToString("x".repeat(64).getBytes(StandardCharsets.UTF_8)));
            properties.setAccessExpiration(3L);
            JwtTokenProvider tokens = new JwtTokenProvider(properties);
            tokens.afterPropertiesSet();
            AccessTokenBlacklist writer = new AccessTokenBlacklist(redis, tokens);
            AccessTokenBlacklist reader = new AccessTokenBlacklist(new StringRedisTemplate(connection), tokens);
            JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokens, new JwtExtractor(), reader, new ObjectMapper());
            AuthPrincipal principal = AuthPrincipal.of(1L, "test@example.com", List.of("PARTICIPANT"));
            var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            String token = tokens.createAccessToken(auth);
            String otherSession = tokens.createAccessToken(auth);
            AuthServiceImpl service = new AuthServiceImpl(mock(UserRepository.class), mock(BCryptPasswordEncoder.class),
                    mock(OrganizationRepository.class), mock(RefreshTokenRepository.class), tokens, properties,
                    mock(AdminInvitationRepository.class), mock(AdminAuditLogger.class), writer);

            assertAuthenticated(filter, token, true);
            service.logout(token, null);
            assertThat(reader.contains(token)).isTrue();
            assertAuthenticated(filter, token, false);
            assertAuthenticated(filter, otherSession, true);
            service.logout(token, null); // Repeated logout remains safe.

            long deadline = System.nanoTime() + java.time.Duration.ofSeconds(5).toNanos();
            while (reader.contains(token) && System.nanoTime() < deadline) {
                Thread.sleep(50);
            }
            assertThat(reader.contains(token)).isFalse();
            assertThat(tokens.validateAccessToken(token)).isFalse();
        } finally {
            SecurityContextHolder.clearContext();
            connection.destroy();
        }
    }

    private void assertAuthenticated(JwtAuthenticationFilter filter, String token, boolean expected) throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me/applications");
        request.addHeader("Authorization", "Bearer " + token);
        try {
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                    assertThat(SecurityContextHolder.getContext().getAuthentication() != null).isEqualTo(expected));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
