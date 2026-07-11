package com.api.trekkey.domain.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.api.trekkey.domain.auth.entity.RefreshToken;
import com.api.trekkey.domain.auth.repository.RefreshTokenRepository;
import com.api.trekkey.domain.auth.service.AuthService;
import com.api.trekkey.domain.auth.web.dto.AuthResult;
import com.api.trekkey.domain.auth.web.dto.UserSignInReq;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.organization.entity.OrganizationStatus;
import com.api.trekkey.domain.organization.repository.OrganizationRepository;
import com.api.trekkey.domain.user.entity.MemberType;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_INTEGRATION_TESTS", matches = "true")
@EnabledIfEnvironmentVariable(
        named = "MYSQL_TEST_URL",
        matches = "jdbc:mysql://.+/trekkey_test(?:\\?.*)?"
)
class AuthMySqlIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private User user;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("MYSQL_TEST_URL"));
        registry.add("spring.datasource.username", () -> environment("MYSQL_TEST_USERNAME", "root"));
        registry.add("spring.datasource.password", () -> environment("MYSQL_TEST_PASSWORD", ""));
    }

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        organizationRepository.deleteAllInBatch();

        Organization organization = BeanUtils.instantiateClass(Organization.class);
        ReflectionTestUtils.setField(organization, "name", "통합테스트대학교");
        ReflectionTestUtils.setField(organization, "status", OrganizationStatus.ACTIVE);
        organization = organizationRepository.saveAndFlush(organization);

        user = User.builder()
                .organization(organization)
                .name("홍길동")
                .email("mysql-auth-test@example.com")
                .password(passwordEncoder.encode("password123"))
                .role(UserRole.PARTICIPANT)
                .memberType(MemberType.STUDENT)
                .status(UserStatus.ACTIVE)
                .studentId("20240001")
                .major("컴퓨터공학부")
                .build();
        user = userRepository.saveAndFlush(user);
    }

    @AfterEach
    void tearDown() {
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        organizationRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("로그인은 refresh token 원문 대신 해시와 family를 MySQL에 저장한다")
    void signIn_persistsHashedRefreshTokenAndFamily() {
        AuthResult result = signIn();

        TokenState stored = tokenStateByRawToken(result.refreshToken());

        assertThat(stored.tokenHash()).isEqualTo(sha256(result.refreshToken())).hasSize(64);
        assertThat(stored.tokenHash()).isNotEqualTo(result.refreshToken());
        assertThat(stored.familyId()).isNotBlank().hasSize(36);
        assertThat(stored.revoked()).isFalse();
        assertThat(stored.userId()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("폐기된 refresh token 재사용 예외 후에도 family 폐기는 커밋된다")
    void reissue_commitsFamilyRevocationAfterReuseException() {
        AuthResult firstLogin = signIn();
        String firstRefreshToken = firstLogin.refreshToken();
        String familyId = tokenStateByRawToken(firstRefreshToken).familyId();
        AuthResult rotated = authService.reissue(firstRefreshToken);

        assertThatThrownBy(() -> authService.reissue(firstRefreshToken))
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(UserErrorResponseCode.USER_INVALID_TOKEN);

        assertThat(familyStates(familyId)).isNotEmpty().allMatch(TokenState::revoked);
        assertThatThrownBy(() -> authService.reissue(rotated.refreshToken()))
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(UserErrorResponseCode.USER_INVALID_TOKEN);
    }

    @Test
    @DisplayName("한 로그인 family를 로그아웃해도 다른 로그인 family는 갱신할 수 있다")
    void logout_revokesOnlyCurrentLoginFamily() {
        AuthResult firstLogin = signIn();
        AuthResult secondLogin = signIn();
        String firstFamilyId = tokenStateByRawToken(firstLogin.refreshToken()).familyId();
        String secondFamilyId = tokenStateByRawToken(secondLogin.refreshToken()).familyId();

        authService.logout(firstLogin.refreshToken());

        assertThat(firstFamilyId).isNotEqualTo(secondFamilyId);
        assertThat(familyStates(firstFamilyId)).isNotEmpty().allMatch(TokenState::revoked);
        assertThat(familyStates(secondFamilyId)).isNotEmpty().noneMatch(TokenState::revoked);

        AuthResult rotatedSecondLogin = authService.reissue(secondLogin.refreshToken());
        assertThat(tokenStateByRawToken(rotatedSecondLogin.refreshToken()).familyId())
                .isEqualTo(secondFamilyId);
        assertThat(familyStates(secondFamilyId).stream().filter(state -> !state.revoked()))
                .hasSize(1);
    }

    @Test
    @Timeout(20)
    @DisplayName("동일 refresh token의 동시 갱신은 하나만 성공하고 family 전체를 폐기한다")
    void concurrentReissue_allowsOneRotationThenRevokesFamily() throws Exception {
        AuthResult login = signIn();
        String refreshToken = login.refreshToken();
        String familyId = tokenStateByRawToken(refreshToken).familyId();
        AtomicInteger successes = new AtomicInteger();
        List<Throwable> failures = Collections.synchronizedList(new java.util.ArrayList<>());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> first = executor.submit(() -> reissueAtSameTime(
                    refreshToken, ready, start, successes, failures));
            Future<?> second = executor.submit(() -> reissueAtSameTime(
                    refreshToken, ready, start, successes, failures));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(successes).hasValue(1);
        assertThat(failures).hasSize(1);
        assertThat(failures.getFirst()).isInstanceOf(CustomException.class);
        assertThat(((CustomException) failures.getFirst()).getBaseResponseCode())
                .isEqualTo(UserErrorResponseCode.USER_INVALID_TOKEN);
        assertThat(familyStates(familyId)).isNotEmpty().allMatch(TokenState::revoked);
    }

    private void reissueAtSameTime(
            String refreshToken,
            CountDownLatch ready,
            CountDownLatch start,
            AtomicInteger successes,
            List<Throwable> failures
    ) {
        ready.countDown();
        try {
            start.await();
            authService.reissue(refreshToken);
            successes.incrementAndGet();
        } catch (Throwable throwable) {
            failures.add(throwable);
        }
    }

    private AuthResult signIn() {
        UserSignInReq request = new UserSignInReq();
        ReflectionTestUtils.setField(request, "email", user.getEmail());
        ReflectionTestUtils.setField(request, "password", "password123");
        return authService.signIn(request);
    }

    private TokenState tokenStateByRawToken(String rawToken) {
        return transactionTemplate.execute(status -> entityManager.createQuery("""
                        select rt
                        from RefreshToken rt
                        where rt.tokenHash = :tokenHash
                        """, RefreshToken.class)
                .setParameter("tokenHash", sha256(rawToken))
                .getResultStream()
                .map(TokenState::from)
                .findFirst()
                .orElseThrow());
    }

    private List<TokenState> familyStates(String familyId) {
        return transactionTemplate.execute(status -> entityManager.createQuery("""
                        select rt
                        from RefreshToken rt
                        where rt.familyId = :familyId
                        order by rt.id
                        """, RefreshToken.class)
                .setParameter("familyId", familyId)
                .getResultList()
                .stream()
                .map(TokenState::from)
                .toList());
    }

    private static String environment(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null ? defaultValue : value;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record TokenState(
            Long id,
            Long userId,
            String tokenHash,
            String familyId,
            boolean revoked
    ) {
        private static TokenState from(RefreshToken token) {
            return new TokenState(
                    token.getId(),
                    token.getUser().getId(),
                    token.getTokenHash(),
                    token.getFamilyId(),
                    token.isRevoked()
            );
        }
    }
}
