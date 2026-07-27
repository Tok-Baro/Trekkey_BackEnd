package com.api.trekkey.domain.contest.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStage;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.ParticipationType;
import com.api.trekkey.domain.contest.entity.ReviewCriterion;
import com.api.trekkey.domain.contest.entity.StagePassRule;
import com.api.trekkey.domain.contest.entity.StageStatus;
import com.api.trekkey.domain.contest.entity.StageTargetType;
import com.api.trekkey.domain.contest.entity.StageType;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.contest.repository.ContestStageRepository;
import com.api.trekkey.domain.contest.repository.ReviewCriterionRepository;
import com.api.trekkey.domain.contest.service.ContestCommandService;
import com.api.trekkey.domain.contest.web.dto.ContestCreateReq;
import com.api.trekkey.domain.contest.web.dto.CriterionReq;
import com.api.trekkey.domain.contest.web.dto.StageReq;
import com.api.trekkey.domain.contest.web.dto.StageStatusUpdateReq;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.organization.entity.OrganizationStatus;
import com.api.trekkey.domain.organization.repository.OrganizationRepository;
import com.api.trekkey.domain.user.entity.MemberType;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import jakarta.persistence.EntityManager;
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
class ContestReviewConfigurationMySqlIntegrationTest {

    @Autowired
    private ContestCommandService contestCommandService;

    @Autowired
    private ReviewCriterionRepository reviewCriterionRepository;

    @Autowired
    private ContestStageRepository contestStageRepository;

    @Autowired
    private ContestRepository contestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Organization organization;
    private User admin;
    private Contest contest;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("MYSQL_TEST_URL"));
        registry.add("spring.datasource.username", () -> environment("MYSQL_TEST_USERNAME", "root"));
        registry.add("spring.datasource.password", () -> environment("MYSQL_TEST_PASSWORD", ""));
    }

    @BeforeEach
    void setUp() {
        deleteTestData();

        organization = BeanUtils.instantiateClass(Organization.class);
        org.springframework.test.util.ReflectionTestUtils.setField(
                organization,
                "name",
                "심사 통합테스트대학교"
        );
        org.springframework.test.util.ReflectionTestUtils.setField(
                organization,
                "status",
                OrganizationStatus.ACTIVE
        );
        organization = organizationRepository.saveAndFlush(organization);

        admin = userRepository.saveAndFlush(User.builder()
                .organization(organization)
                .name("심사 관리자")
                .email("mysql-review-admin@example.com")
                .password("encoded-password")
                .role(UserRole.ADMIN)
                .memberType(MemberType.STAFF)
                .status(UserStatus.ACTIVE)
                .build());

        contest = contestRepository.saveAndFlush(Contest.builder()
                .organization(organization)
                .ownerUser(admin)
                .title("기존 심사 대회")
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
    @DisplayName("MySQL에서 준비 중인 단계 순서를 맞바꿔도 sequence unique 제약과 충돌하지 않는다")
    void updateContest_swapsStageSequenceWithoutUniqueConstraintViolation() {
        ContestStage application =
                contestStageRepository.saveAndFlush(stage("참가 신청", StageType.APPLICATION, 1));
        ContestStage submission =
                contestStageRepository.saveAndFlush(stage("제출", StageType.SUBMISSION, 2));

        contestCommandService.updateContest(
                admin.getId(),
                contest.getPublicId(),
                contestReq(List.of(
                        stageReq(submission, 1, null),
                        stageReq(application, 2, null)
                ))
        );

        List<Long> orderedStageIds = transactionTemplate.execute(status ->
                entityManager.createQuery("""
                                select stage.id
                                from ContestStage stage
                                where stage.contest.id = :contestId
                                order by stage.sequenceNo
                                """, Long.class)
                        .setParameter("contestId", contest.getId())
                        .getResultList());

        assertThat(orderedStageIds).containsExactly(submission.getId(), application.getId());
    }

    @Test
    @DisplayName("MySQL에서 기준 수정은 PK를 유지하고 누락 기준은 삭제 대신 비활성화한다")
    void updateContest_preservesCriterionIdentityAndDeactivatesOmittedCriterion() {
        ContestStage review =
                contestStageRepository.saveAndFlush(stage("서류 심사", StageType.REVIEW, 1));
        ReviewCriterion creativity = reviewCriterionRepository.saveAndFlush(
                criterion(review, "creativity", "창의성", 30, 1));
        ReviewCriterion completeness = reviewCriterionRepository.saveAndFlush(
                criterion(review, "completeness", "완성도", 30, 2));

        contestCommandService.updateContest(
                admin.getId(),
                contest.getPublicId(),
                contestReq(List.of(stageReq(
                        review,
                        1,
                        List.of(
                                new CriterionReq(
                                        creativity.getId(),
                                        "creativity",
                                        "창의성 개선",
                                        40,
                                        1
                                ),
                                new CriterionReq(null, "impact", "파급력", 20, 2)
                        )
                )))
        );

        List<CriterionSnapshot> criteria = transactionTemplate.execute(status ->
                entityManager.createQuery("""
                                select criterion
                                from ReviewCriterion criterion
                                where criterion.contestStage.id = :stageId
                                order by criterion.id
                                """, ReviewCriterion.class)
                        .setParameter("stageId", review.getId())
                        .getResultList()
                        .stream()
                        .map(CriterionSnapshot::from)
                        .toList());

        assertThat(criteria).hasSize(3);
        assertThat(criteria)
                .filteredOn(snapshot -> snapshot.id().equals(creativity.getId()))
                .containsExactly(new CriterionSnapshot(
                        creativity.getId(),
                        "creativity",
                        "창의성 개선",
                        40,
                        true
                ));
        assertThat(criteria)
                .filteredOn(snapshot -> snapshot.id().equals(completeness.getId()))
                .extracting(CriterionSnapshot::active)
                .containsExactly(false);
        assertThat(criteria)
                .filteredOn(snapshot -> snapshot.code().equals("impact"))
                .extracting(CriterionSnapshot::active)
                .containsExactly(true);
    }

    @Test
    @Timeout(15)
    @DisplayName("단계 오픈은 동시 기준 변경을 기다린 뒤 최신 활성 기준을 검사한다")
    void updateStageStatus_waitsForCriterionChangeAndReadsCurrentState() throws Exception {
        ContestStage review =
                contestStageRepository.saveAndFlush(stage("서류 심사", StageType.REVIEW, 1));
        reviewCriterionRepository.saveAndFlush(
                criterion(review, "creativity", "창의성", 30, 1));

        CountDownLatch criterionChanged = new CountDownLatch(1);
        CountDownLatch allowCriterionCommit = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> criterionWriter = executor.submit(() -> {
                transactionTemplate.executeWithoutResult(status -> {
                    contestStageRepository.findByIdForUpdate(review.getId()).orElseThrow();
                    List<ReviewCriterion> criteria =
                            reviewCriterionRepository
                                    .findAllForUpdateByContestStageIdInOrderBySortOrderAsc(
                                            List.of(review.getId()));
                    criteria.getFirst().deactivate();
                    reviewCriterionRepository.saveAll(criteria);
                    criterionChanged.countDown();
                    await(allowCriterionCommit);
                });
            });

            assertThat(criterionChanged.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Throwable> stageOpener = executor.submit(() -> {
                try {
                    contestCommandService.updateStageStatus(
                            admin.getId(),
                            review.getId(),
                            new StageStatusUpdateReq(StageStatus.OPEN)
                    );
                    return null;
                } catch (Throwable throwable) {
                    return throwable;
                }
            });

            assertThatThrownBy(() -> stageOpener.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            allowCriterionCommit.countDown();
            criterionWriter.get(5, TimeUnit.SECONDS);

            Throwable openingFailure = stageOpener.get(5, TimeUnit.SECONDS);
            assertThat(openingFailure).isInstanceOf(CustomException.class);
            assertThat(((CustomException) openingFailure).getBaseResponseCode())
                    .isEqualTo(ContestErrorResponseCode.REVIEW_CRITERION_REQUIRED);
        } finally {
            allowCriterionCommit.countDown();
            executor.shutdownNow();
        }

        StageStatus storedStatus = transactionTemplate.execute(status ->
                entityManager.createQuery("""
                                select stage.status
                                from ContestStage stage
                                where stage.id = :stageId
                                """, StageStatus.class)
                        .setParameter("stageId", review.getId())
                        .getSingleResult());
        assertThat(storedStatus).isEqualTo(StageStatus.PREPARING);
    }

    private ContestStage stage(String name, StageType stageType, int sequenceNo) {
        return ContestStage.builder()
                .contest(contest)
                .name(name)
                .stageType(stageType)
                .sequenceNo(sequenceNo)
                .status(StageStatus.PREPARING)
                .targetType(stageType.supportsReviewCriteria()
                        ? StageTargetType.ALL_SUBMISSIONS
                        : null)
                .passRule(stageType.supportsReviewCriteria() ? StagePassRule.FINAL : null)
                .build();
    }

    private ReviewCriterion criterion(
            ContestStage stage,
            String code,
            String label,
            int maxScore,
            int sortOrder
    ) {
        return ReviewCriterion.builder()
                .contestStage(stage)
                .code(code)
                .label(label)
                .maxScore(maxScore)
                .sortOrder(sortOrder)
                .active(true)
                .build();
    }

    private StageReq stageReq(
            ContestStage stage,
            int sequenceNo,
            List<CriterionReq> criteria
    ) {
        return new StageReq(
                stage.getId(),
                stage.getName(),
                stage.getStageType(),
                sequenceNo,
                StageStatus.PREPARING,
                stage.getStartsAt(),
                stage.getEndsAt(),
                stage.getTargetType(),
                stage.getPassRule(),
                stage.getPassCount(),
                stage.getMinScore(),
                criteria
        );
    }

    private ContestCreateReq contestReq(List<StageReq> stages) {
        return new ContestCreateReq(
                "수정된 심사 대회",
                "교무처",
                ContestStatus.PREPARING,
                ParticipationType.BOTH,
                1,
                null,
                "통합 테스트",
                "재학생",
                "온라인",
                "상장",
                null,
                "<p>본문</p>",
                stages
        );
    }

    private void deleteTestData() {
        reviewCriterionRepository.deleteAllInBatch();
        contestStageRepository.deleteAllInBatch();
        contestRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        organizationRepository.deleteAllInBatch();
    }

    private static String environment(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null ? defaultValue : value;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시성 테스트 대기 시간이 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시성 테스트 대기가 중단되었습니다.", exception);
        }
    }

    private record CriterionSnapshot(
            Long id,
            String code,
            String label,
            int maxScore,
            boolean active
    ) {
        private static CriterionSnapshot from(ReviewCriterion criterion) {
            return new CriterionSnapshot(
                    criterion.getId(),
                    criterion.getCode(),
                    criterion.getLabel(),
                    criterion.getMaxScore(),
                    criterion.isActive()
            );
        }
    }
}
