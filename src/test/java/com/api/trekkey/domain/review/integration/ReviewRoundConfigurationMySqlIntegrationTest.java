package com.api.trekkey.domain.review.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.api.trekkey.domain.audit.repository.AdminAuditLogRepository;
import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.ParticipationType;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.organization.entity.OrganizationStatus;
import com.api.trekkey.domain.organization.repository.OrganizationRepository;
import com.api.trekkey.domain.review.entity.ReviewCriterion;
import com.api.trekkey.domain.review.entity.ReviewRound;
import com.api.trekkey.domain.review.entity.ReviewRoundDecisionRule;
import com.api.trekkey.domain.review.entity.ReviewRoundStatus;
import com.api.trekkey.domain.review.entity.ReviewRoundTargetType;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.repository.ReviewCriterionRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundRepository;
import com.api.trekkey.domain.review.admin.service.ReviewRoundAdminService;
import com.api.trekkey.domain.review.admin.web.dto.request.ReviewRoundCriterionReq;
import com.api.trekkey.domain.review.admin.web.dto.request.ReviewRoundSaveReq;
import com.api.trekkey.domain.review.admin.web.dto.response.ReviewRoundRes;
import com.api.trekkey.domain.user.entity.MemberType;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@EnabledIfEnvironmentVariable(
        named = "RUN_MYSQL_INTEGRATION_TESTS",
        matches = "true"
)
@EnabledIfEnvironmentVariable(
        named = "MYSQL_TEST_URL",
        matches = "jdbc:mysql://.+/trekkey_test(?:\\?.*)?"
)
class ReviewRoundConfigurationMySqlIntegrationTest {

    @Autowired
    private ReviewRoundAdminService reviewRoundAdminService;

    @Autowired
    private ReviewCriterionRepository reviewCriterionRepository;

    @Autowired
    private ReviewRoundRepository reviewRoundRepository;

    @Autowired
    private ContestRepository contestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private AdminAuditLogRepository adminAuditLogRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private User admin;
    private Contest contest;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () -> System.getenv("MYSQL_TEST_URL")
        );
        registry.add(
                "spring.datasource.username",
                () -> environment("MYSQL_TEST_USERNAME", "root")
        );
        registry.add(
                "spring.datasource.password",
                () -> environment("MYSQL_TEST_PASSWORD", "")
        );
    }

    @BeforeEach
    void setUp() {
        deleteTestData();

        Organization organization =
                BeanUtils.instantiateClass(Organization.class);
        ReflectionTestUtils.setField(
                organization,
                "name",
                "심사 라운드 통합테스트대학교"
        );
        ReflectionTestUtils.setField(
                organization,
                "status",
                OrganizationStatus.ACTIVE
        );
        organization = organizationRepository.saveAndFlush(organization);

        admin = userRepository.saveAndFlush(User.builder()
                .organization(organization)
                .name("심사 라운드 관리자")
                .email("mysql-round-admin@example.com")
                .password("encoded-password")
                .role(UserRole.ADMIN)
                .memberType(MemberType.STAFF)
                .status(UserStatus.ACTIVE)
                .build());

        contest = contestRepository.saveAndFlush(Contest.builder()
                .organization(organization)
                .ownerUser(admin)
                .title("심사 라운드 통합 테스트 대회")
                .department("교무처")
                .status(ContestStatus.PREPARING)
                .participationType(ParticipationType.BOTH)
                .awardCount(1)
                .summary("통합 테스트")
                .target("재학생")
                .applicationMethod("온라인")
                .benefits("상장")
                .detailHtml("<p>본문</p>")
                .build());
    }

    @AfterEach
    void tearDown() {
        deleteTestData();
    }

    @Test
    @DisplayName("평가 기준 수정은 PK를 유지하고 누락한 기준은 비활성화한다")
    void updateRound_preservesCriterionIdentityAndDeactivatesOmittedOne() {
        ReviewRoundRes created = reviewRoundAdminService.createRound(
                admin.getId(),
                contest.getPublicId(),
                roundRequest(List.of(
                        criterion(null, "creativity", "창의성", 30, 1),
                        criterion(null, "completeness", "완성도", 30, 2)
                ))
        );
        Long creativityId = created.criteria().getFirst().id();
        Long completenessId = created.criteria().get(1).id();

        ReviewRoundRes updated = reviewRoundAdminService.updateRound(
                admin.getId(),
                contest.getPublicId(),
                created.id(),
                roundRequest(List.of(
                        criterion(
                                creativityId,
                                "creativity",
                                "창의성 개선",
                                40,
                                1
                        ),
                        criterion(null, "impact", "파급력", 20, 2)
                ))
        );

        List<ReviewCriterion> stored = reviewCriterionRepository
                .findAllByReviewRoundIdInOrderBySortOrderAsc(
                        List.of(created.id()));
        assertThat(stored).hasSize(3);
        assertThat(stored)
                .filteredOn(item -> item.getId().equals(creativityId))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getLabel()).isEqualTo("창의성 개선");
                    assertThat(item.getMaxScore()).isEqualTo(40);
                    assertThat(item.isActive()).isTrue();
                });
        assertThat(stored)
                .filteredOn(item -> item.getId().equals(completenessId))
                .extracting(ReviewCriterion::isActive)
                .containsExactly(false);
        assertThat(updated.criteria())
                .extracting(item -> item.code())
                .containsExactly("creativity", "impact");
    }

    @Test
    @DisplayName("다음 순서가 아닌 라운드 번호를 만들 수 없다")
    void createRound_rejectsNonSequentialRoundNo() {
        ReviewRoundSaveReq request = roundRequest(List.of(
                criterion(null, "creativity", "창의성", 30, 1)
        ));
        reviewRoundAdminService.createRound(
                admin.getId(),
                contest.getPublicId(),
                request
        );

        assertThatThrownBy(() -> reviewRoundAdminService.createRound(
                admin.getId(),
                contest.getPublicId(),
                request
        ))
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception -> assertThat(
                                exception.getBaseResponseCode())
                                .isEqualTo(ReviewErrorResponseCode
                                        .REVIEW_ROUND_SEQUENCE_INVALID)
                );
    }

    @Test
    @Timeout(15)
    @DisplayName("라운드 오픈은 진행 중인 기준 변경을 기다린 뒤 최신 기준을 검사한다")
    void openRound_waitsForCriterionChangeAndReadsCurrentState()
            throws Exception {
        ReviewRoundRes created = reviewRoundAdminService.createRound(
                admin.getId(),
                contest.getPublicId(),
                roundRequest(List.of(
                        criterion(null, "creativity", "창의성", 30, 1)
                ))
        );

        CountDownLatch criterionChanged = new CountDownLatch(1);
        CountDownLatch allowCriterionCommit = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> criterionWriter = executor.submit(() ->
                    transactionTemplate.executeWithoutResult(status -> {
                        reviewRoundRepository
                                .findByIdForUpdate(created.id())
                                .orElseThrow();
                        List<ReviewCriterion> criteria =
                                reviewCriterionRepository
                                        .findAllForUpdateByReviewRoundIdInOrderBySortOrderAsc(
                                                List.of(created.id()));
                        criteria.getFirst().deactivate();
                        reviewCriterionRepository.saveAll(criteria);
                        criterionChanged.countDown();
                        await(allowCriterionCommit);
                    }));

            assertThat(criterionChanged.await(5, TimeUnit.SECONDS))
                    .isTrue();

            Future<Throwable> roundOpener = executor.submit(() -> {
                try {
                    reviewRoundAdminService.openRound(
                            admin.getId(),
                            contest.getPublicId(),
                            created.id()
                    );
                    return null;
                } catch (Throwable throwable) {
                    return throwable;
                }
            });

            assertThatThrownBy(() ->
                    roundOpener.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            allowCriterionCommit.countDown();
            criterionWriter.get(5, TimeUnit.SECONDS);

            Throwable openingFailure =
                    roundOpener.get(5, TimeUnit.SECONDS);
            assertThat(openingFailure)
                    .isInstanceOf(CustomException.class);
            assertThat(((CustomException) openingFailure)
                    .getBaseResponseCode())
                    .isEqualTo(ReviewErrorResponseCode
                            .REVIEW_ROUND_CRITERION_REQUIRED);
        } finally {
            allowCriterionCommit.countDown();
            executor.shutdownNow();
        }

        ReviewRound stored = reviewRoundRepository
                .findById(created.id())
                .orElseThrow();
        assertThat(stored.getStatus())
                .isEqualTo(ReviewRoundStatus.PREPARING);
    }

    private ReviewRoundSaveReq roundRequest(
            List<ReviewRoundCriterionReq> criteria
    ) {
        LocalDateTime now = LocalDateTime.now();
        return new ReviewRoundSaveReq(
                1,
                "1차 심사",
                now.minusHours(1),
                now.plusDays(1),
                ReviewRoundTargetType.ALL_SUBMISSIONS,
                ReviewRoundDecisionRule.MANUAL,
                null,
                null,
                criteria
        );
    }

    private ReviewRoundCriterionReq criterion(
            Long id,
            String code,
            String label,
            int maxScore,
            int sortOrder
    ) {
        return new ReviewRoundCriterionReq(
                id,
                code,
                label,
                maxScore,
                sortOrder
        );
    }

    private void deleteTestData() {
        adminAuditLogRepository.deleteAllInBatch();
        reviewCriterionRepository.deleteAllInBatch();
        reviewRoundRepository.deleteAllInBatch();
        contestRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        organizationRepository.deleteAllInBatch();
    }

    private static String environment(
            String name,
            String defaultValue
    ) {
        String value = System.getenv(name);
        return value == null ? defaultValue : value;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "동시성 테스트 대기 시간이 초과되었습니다."
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "동시성 테스트 대기가 중단되었습니다.",
                    exception
            );
        }
    }
}
