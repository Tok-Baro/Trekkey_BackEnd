package com.api.trekkey.domain.review.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.api.trekkey.domain.audit.repository.AdminAuditLogRepository;
import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.ParticipationType;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.credential.integration.WorkCredentialIssuer;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.organization.entity.OrganizationStatus;
import com.api.trekkey.domain.organization.repository.OrganizationRepository;
import com.api.trekkey.domain.review.entity.ReviewRound;
import com.api.trekkey.domain.review.entity.ReviewRoundDecisionRule;
import com.api.trekkey.domain.review.entity.ReviewRoundEntry;
import com.api.trekkey.domain.review.entity.ReviewRoundEntryStatus;
import com.api.trekkey.domain.review.entity.ReviewRoundStatus;
import com.api.trekkey.domain.review.entity.ReviewRoundTargetType;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.repository.ReviewCriterionRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundEntryRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundRepository;
import com.api.trekkey.domain.review.admin.service.ReviewRoundAdminService;
import com.api.trekkey.domain.review.admin.service.ReviewRoundEntryAdminService;
import com.api.trekkey.domain.review.admin.web.dto.request.ReviewRoundEntryPrepareReq;
import com.api.trekkey.domain.review.admin.web.dto.response.ReviewRoundEntryRes;
import com.api.trekkey.domain.review.admin.web.dto.response.ReviewRoundRes;
import com.api.trekkey.domain.submission.entity.Submission;
import com.api.trekkey.domain.submission.entity.SubmissionStatus;
import com.api.trekkey.domain.submission.repository.SubmissionRepository;
import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.domain.team.entity.TeamStatus;
import com.api.trekkey.domain.team.repository.TeamRepository;
import com.api.trekkey.domain.user.entity.MemberType;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;

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
class ReviewRoundEntryMySqlIntegrationTest {

    @Autowired
    private ReviewRoundEntryAdminService reviewRoundEntryAdminService;

    @Autowired
    private ReviewRoundAdminService reviewRoundAdminService;

    @Autowired
    private ReviewRoundEntryRepository reviewRoundEntryRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private TeamRepository teamRepository;

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

    @MockitoBean
    private WorkCredentialIssuer workCredentialIssuer;

    private User admin;
    private Contest contest;
    private ReviewRound reviewRound;
    private Submission submission;

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
                "심사 대상 통합테스트대학교"
        );
        ReflectionTestUtils.setField(
                organization,
                "status",
                OrganizationStatus.ACTIVE
        );
        organization = organizationRepository.saveAndFlush(organization);

        admin = userRepository.saveAndFlush(User.builder()
                .organization(organization)
                .name("심사 관리자")
                .email("mysql-entry-admin@example.com")
                .password("encoded-password")
                .role(UserRole.ADMIN)
                .memberType(MemberType.STAFF)
                .status(UserStatus.ACTIVE)
                .build());

        User participant = userRepository.saveAndFlush(User.builder()
                .organization(organization)
                .name("김참가")
                .email("mysql-entry-participant@example.com")
                .password("encoded-password")
                .role(UserRole.PARTICIPANT)
                .memberType(MemberType.STUDENT)
                .status(UserStatus.ACTIVE)
                .studentId("20260001")
                .major("컴퓨터공학부")
                .build());

        contest = contestRepository.saveAndFlush(Contest.builder()
                .organization(organization)
                .ownerUser(admin)
                .title("심사 대상 통합 테스트 대회")
                .department("교무처")
                .status(ContestStatus.REVIEWING)
                .participationType(ParticipationType.TEAM)
                .awardCount(1)
                .summary("심사 대상 통합 테스트")
                .target("재학생")
                .applicationMethod("온라인")
                .benefits("상장")
                .detailHtml("<p>본문</p>")
                .build());

        LocalDateTime now = LocalDateTime.now();
        reviewRound = reviewRoundRepository.saveAndFlush(
                ReviewRound.builder()
                        .contest(contest)
                        .name("1차 심사")
                        .roundNo(1)
                        .status(ReviewRoundStatus.PREPARING)
                        .startsAt(now.minusHours(1))
                        .endsAt(now.plusDays(1))
                        .targetType(ReviewRoundTargetType.MANUAL)
                        .decisionRule(ReviewRoundDecisionRule.MANUAL)
                        .build()
        );

        Team team = Team.builder()
                .contest(contest)
                .leaderUser(participant)
                .name("트랙키 팀")
                .leaderName("김참가")
                .major("컴퓨터공학부")
                .memberCount(1)
                .status(TeamStatus.APPROVED)
                .contactEmail("participant@example.com")
                .phone("010-1234-5678")
                .motivation("학교 문제를 해결합니다.")
                .build();
        team.finalizeParticipation(LocalDateTime.now().minusDays(2));
        team = teamRepository.saveAndFlush(team);

        submission = submissionRepository.saveAndFlush(
                Submission.builder()
                        .team(team)
                        .title("AI 캠퍼스")
                        .status(SubmissionStatus.SUBMITTED)
                        .submittedAt(LocalDateTime.now().minusDays(1))
                        .build()
        );
    }

    @AfterEach
    void tearDown() {
        deleteTestData();
    }

    @Test
    @DisplayName("라운드를 연 뒤에는 대상 재준비를 거부하고 기존 대상을 유지한다")
    void prepareEntries_rejectsRetryAfterRoundOpens() {
        List<ReviewRoundEntryRes> first =
                reviewRoundEntryAdminService.prepareEntries(
                        admin.getId(),
                        contest.getPublicId(),
                        reviewRound.getId(),
                        manualTargetRequest()
                );
        ReviewRoundRes opened = reviewRoundAdminService.openRound(
                admin.getId(),
                contest.getPublicId(),
                reviewRound.getId()
        );

        assertThatThrownBy(() ->
                reviewRoundEntryAdminService.prepareEntries(
                        admin.getId(),
                        contest.getPublicId(),
                        reviewRound.getId(),
                        manualTargetRequest()
                ))
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception -> assertThat(
                                exception.getBaseResponseCode())
                                .isEqualTo(ReviewErrorResponseCode
                                        .REVIEW_ENTRY_PREPARATION_NOT_ALLOWED)
                );

        assertThat(first).hasSize(1);
        assertThat(opened.status()).isEqualTo(ReviewRoundStatus.OPEN);
        assertThat(reviewRoundEntryRepository.count()).isEqualTo(1);
        assertThat(submissionRepository.findById(submission.getId())
                .orElseThrow()
                .getFinalizedAt()).isNotNull();
    }

    @Test
    @Timeout(15)
    @DisplayName("동시에 대상을 준비해도 둘 다 성공하고 엔트리는 한 건만 생성된다")
    void concurrentPrepareEntries_returnsTheSameEntry() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<List<ReviewRoundEntryRes>>> futures =
                new ArrayList<>();

        try {
            for (int index = 0; index < 2; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    await(start);
                    return reviewRoundEntryAdminService.prepareEntries(
                            admin.getId(),
                            contest.getPublicId(),
                            reviewRound.getId(),
                            manualTargetRequest()
                    );
                }));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Long> returnedEntryIds = new ArrayList<>();
            for (Future<List<ReviewRoundEntryRes>> future : futures) {
                List<ReviewRoundEntryRes> entries =
                        future.get(10, TimeUnit.SECONDS);
                assertThat(entries).hasSize(1);
                returnedEntryIds.add(entries.getFirst().id());
            }
            assertThat(returnedEntryIds).hasSize(2);
            assertThat(returnedEntryIds.get(0))
                    .isEqualTo(returnedEntryIds.get(1));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(reviewRoundEntryRepository.count()).isEqualTo(1);
    }

    @Test
    @Timeout(15)
    @DisplayName("단계 오픈은 진행 중인 대상 저장을 기다린 뒤 최신 엔트리를 확인한다")
    void openStage_waitsForEntryCommitAndReadsCurrentState()
            throws Exception {
        CountDownLatch entryInserted = new CountDownLatch(1);
        CountDownLatch allowEntryCommit = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> entryWriter = executor.submit(() ->
                    transactionTemplate.executeWithoutResult(status -> {
                        ReviewRound lockedRound = reviewRoundRepository
                                .findByIdForUpdate(reviewRound.getId())
                                .orElseThrow();
                        Submission storedSubmission = submissionRepository
                                .findById(submission.getId())
                                .orElseThrow();
                        reviewRoundEntryRepository.saveAndFlush(
                                ReviewRoundEntry.builder()
                                        .reviewRound(lockedRound)
                                        .submission(storedSubmission)
                                        .status(
                                                ReviewRoundEntryStatus.ELIGIBLE)
                                        .build()
                        );
                        entryInserted.countDown();
                        await(allowEntryCommit);
                    }));

            assertThat(entryInserted.await(5, TimeUnit.SECONDS)).isTrue();

            Future<ReviewRoundRes> stageOpener = executor.submit(() ->
                    reviewRoundAdminService.openRound(
                            admin.getId(),
                            contest.getPublicId(),
                            reviewRound.getId()
                    ));

            assertThatThrownBy(() ->
                    stageOpener.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            allowEntryCommit.countDown();
            entryWriter.get(5, TimeUnit.SECONDS);

            assertThat(stageOpener.get(5, TimeUnit.SECONDS).status())
                    .isEqualTo(ReviewRoundStatus.OPEN);
        } finally {
            allowEntryCommit.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("작품 Credential 발급 실패 시 라운드 시작과 제출 확정을 함께 롤백한다")
    void openRound_rollsBackWhenWorkCredentialIssuanceFails() {
        contest.changeStatus(ContestStatus.APPLICATION_OPEN);
        contestRepository.saveAndFlush(contest);
        List<ReviewRoundEntryRes> entries =
                reviewRoundEntryAdminService.prepareEntries(
                        admin.getId(),
                        contest.getPublicId(),
                        reviewRound.getId(),
                        manualTargetRequest());
        given(workCredentialIssuer.issueForFinalizedSubmission(any()))
                .willThrow(new IllegalStateException(
                        "credential issuance failed"));

        assertThatThrownBy(() -> reviewRoundAdminService.openRound(
                admin.getId(),
                contest.getPublicId(),
                reviewRound.getId()
        )).isInstanceOf(IllegalStateException.class);

        assertThat(contestRepository.findById(contest.getId())
                .orElseThrow()
                .getStatus()).isEqualTo(ContestStatus.APPLICATION_OPEN);
        assertThat(reviewRoundRepository.findById(reviewRound.getId())
                .orElseThrow()
                .getStatus()).isEqualTo(ReviewRoundStatus.PREPARING);
        assertThat(reviewRoundEntryRepository.findById(
                entries.getFirst().id()).orElseThrow().getStatus())
                .isEqualTo(ReviewRoundEntryStatus.ELIGIBLE);
        assertThat(submissionRepository.findById(submission.getId())
                .orElseThrow()
                .getFinalizedAt()).isNull();
    }

    private void deleteTestData() {
        adminAuditLogRepository.deleteAllInBatch();
        reviewRoundEntryRepository.deleteAllInBatch();
        submissionRepository.deleteAllInBatch();
        teamRepository.deleteAllInBatch();
        reviewCriterionRepository.deleteAllInBatch();
        reviewRoundRepository.deleteAllInBatch();
        contestRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        organizationRepository.deleteAllInBatch();
    }

    private ReviewRoundEntryPrepareReq manualTargetRequest() {
        return new ReviewRoundEntryPrepareReq(
                List.of(submission.getPublicId()));
    }

    private static String environment(String name, String defaultValue) {
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
