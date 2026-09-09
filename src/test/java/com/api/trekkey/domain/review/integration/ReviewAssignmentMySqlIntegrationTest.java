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
import com.api.trekkey.domain.review.entity.ContestJudge;
import com.api.trekkey.domain.review.entity.ReviewAssignmentStatus;
import com.api.trekkey.domain.review.entity.ReviewRound;
import com.api.trekkey.domain.review.entity.ReviewRoundDecisionRule;
import com.api.trekkey.domain.review.entity.ReviewRoundEntry;
import com.api.trekkey.domain.review.entity.ReviewRoundEntryStatus;
import com.api.trekkey.domain.review.entity.ReviewRoundStatus;
import com.api.trekkey.domain.review.entity.ReviewRoundTargetType;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.repository.ContestJudgeRepository;
import com.api.trekkey.domain.review.repository.ReviewAssignmentRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundEntryRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundRepository;
import com.api.trekkey.domain.review.publicapi.service.ReviewAccessService;
import com.api.trekkey.domain.review.admin.service.ReviewAssignmentAdminService;
import com.api.trekkey.domain.review.admin.service.ContestJudgeAdminService;
import com.api.trekkey.domain.review.admin.web.dto.request.ContestJudgeUpdateReq;
import com.api.trekkey.domain.review.support.ReviewLinkTokenManager;
import com.api.trekkey.domain.review.publicapi.web.dto.request.ReviewAccessReq;
import com.api.trekkey.domain.review.admin.web.dto.request.ReviewAssignmentPrepareReq;
import com.api.trekkey.domain.review.publicapi.web.dto.response.ReviewAccessRes;
import com.api.trekkey.domain.review.admin.web.dto.response.ReviewAssignmentRes;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
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
class ReviewAssignmentMySqlIntegrationTest {

    @Autowired
    private ReviewAssignmentAdminService reviewAssignmentAdminService;

    @Autowired private ContestJudgeAdminService contestJudgeAdminService;
    @Autowired private PlatformTransactionManager transactionManager;

    @Autowired
    private ReviewAccessService reviewAccessService;

    @Autowired
    private ReviewLinkTokenManager reviewLinkTokenManager;

    @Autowired
    private ReviewAssignmentRepository reviewAssignmentRepository;

    @Autowired
    private ReviewRoundEntryRepository reviewRoundEntryRepository;

    @Autowired
    private ContestJudgeRepository contestJudgeRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private TeamRepository teamRepository;

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

    private User admin;
    private Contest contest;
    private ReviewRound reviewRound;
    private ContestJudge judge;

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
                "심사 배정 통합테스트대학교"
        );
        ReflectionTestUtils.setField(
                organization,
                "status",
                OrganizationStatus.ACTIVE
        );
        organization = organizationRepository.saveAndFlush(organization);

        admin = userRepository.saveAndFlush(User.builder()
                .organization(organization)
                .name("심사 배정 관리자")
                .email("mysql-assignment-admin@example.com")
                .password("encoded-password")
                .role(UserRole.ADMIN)
                .memberType(MemberType.STAFF)
                .status(UserStatus.ACTIVE)
                .build());

        User participant = userRepository.saveAndFlush(User.builder()
                .organization(organization)
                .name("김참가")
                .email("mysql-assignment-participant@example.com")
                .password("encoded-password")
                .role(UserRole.PARTICIPANT)
                .memberType(MemberType.STUDENT)
                .status(UserStatus.ACTIVE)
                .studentId("20260002")
                .major("컴퓨터공학부")
                .build());

        contest = contestRepository.saveAndFlush(Contest.builder()
                .organization(organization)
                .ownerUser(admin)
                .title("심사 배정 통합 테스트 대회")
                .department("교무처")
                .status(ContestStatus.REVIEWING)
                .participationType(ParticipationType.TEAM)
                .awardCount(1)
                .summary("심사 배정 통합 테스트")
                .target("재학생")
                .applicationMethod("온라인")
                .benefits("상장")
                .detailHtml("<p>본문</p>")
                .build());

        reviewRound = reviewRoundRepository.saveAndFlush(
                ReviewRound.builder()
                        .contest(contest)
                        .name("1차 심사")
                        .roundNo(1)
                        .status(ReviewRoundStatus.PREPARING)
                        .startsAt(LocalDateTime.now().minusHours(1))
                        .endsAt(LocalDateTime.now().plusDays(1))
                        .targetType(ReviewRoundTargetType.ALL_SUBMISSIONS)
                        .decisionRule(ReviewRoundDecisionRule.MANUAL)
                        .build()
        );

        Team team = teamRepository.saveAndFlush(Team.builder()
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
                .build());

        Submission submission = submissionRepository.saveAndFlush(
                Submission.builder()
                        .team(team)
                        .title("AI 캠퍼스")
                        .status(SubmissionStatus.SUBMITTED)
                        .submittedAt(LocalDateTime.now().minusDays(1))
                        .build()
        );

        reviewRoundEntryRepository.saveAndFlush(
                ReviewRoundEntry.builder()
                        .reviewRound(reviewRound)
                        .submission(submission)
                        .status(ReviewRoundEntryStatus.ELIGIBLE)
                        .build()
        );

        judge = contestJudgeRepository.saveAndFlush(
                ContestJudge.builder()
                        .contest(contest)
                        .name("김심사")
                        .roleLabel("외부 전문가")
                        .build()
        );
    }

    @AfterEach
    void tearDown() {
        deleteTestData();
    }

    @Test
    @DisplayName("같은 심사위원의 평가표 준비를 재호출하면 같은 배정을 반환한다")
    void prepareAssignments_isIdempotent() {
        ReviewAssignmentPrepareReq request =
                new ReviewAssignmentPrepareReq(null);

        List<ReviewAssignmentRes> first =
                reviewAssignmentAdminService.prepareAssignments(
                        admin.getId(),
                        contest.getPublicId(),
                        reviewRound.getId(),
                        judge.getId(),
                        request
                );
        List<ReviewAssignmentRes> retried =
                reviewAssignmentAdminService.prepareAssignments(
                        admin.getId(),
                        contest.getPublicId(),
                        reviewRound.getId(),
                        judge.getId(),
                        request
                );

        assertThat(first).singleElement()
                .satisfies(assignment -> {
                    assertThat(assignment.status())
                            .isEqualTo(ReviewAssignmentStatus.ASSIGNED);
                    assertThat(assignment.judgeId())
                            .isEqualTo(judge.getId());
                    assertThat(assignment.reviewRoundId())
                            .isEqualTo(reviewRound.getId());
                });
        assertThat(retried).singleElement()
                .extracting(ReviewAssignmentRes::id)
                .isEqualTo(first.getFirst().id());
        assertThat(reviewAssignmentRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("MySQL에서 공유 잠금으로 심사 링크를 확인할 수 있다")
    void verifyAccess_supportsMySqlLockingRead() {
        String rawToken = "a".repeat(43);
        LocalDateTime now = LocalDateTime.now();
        judge.issueReviewLink(
                reviewLinkTokenManager.hash(rawToken),
                now.minusMinutes(1),
                now.plusHours(1)
        );
        contestJudgeRepository.saveAndFlush(judge);

        ReviewAccessRes response = reviewAccessService.verifyAccess(
                new ReviewAccessReq(rawToken)
        );

        assertThat(response.judgeName()).isEqualTo(judge.getName());
        assertThat(response.contestPublicId())
                .isEqualTo(contest.getPublicId());
    }

    @Test
    @Timeout(15)
    @DisplayName("같은 심사위원과 라운드의 동시 준비는 중복 배정을 만들지 않는다")
    void concurrentPrepareAssignments_doesNotCreateDuplicates()
            throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<PrepareOutcome>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < 2; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    await(start);
                    return prepareAssignments();
                }));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<PrepareOutcome> outcomes = new ArrayList<>();
            for (Future<PrepareOutcome> future : futures) {
                outcomes.add(future.get(10, TimeUnit.SECONDS));
            }

            List<PrepareOutcome> successes = outcomes.stream()
                    .filter(outcome -> outcome.failure() == null)
                    .toList();
            assertThat(successes).isNotEmpty();

            List<Long> returnedIds = new ArrayList<>();
            for (PrepareOutcome success : successes) {
                assertThat(success.assignments()).singleElement()
                        .satisfies(assignment ->
                                returnedIds.add(assignment.id()));
            }
            assertThat(returnedIds)
                    .allMatch(id -> id.equals(returnedIds.getFirst()));

            List<Throwable> failures = outcomes.stream()
                    .map(PrepareOutcome::failure)
                    .filter(failure -> failure != null)
                    .toList();
            assertThat(failures).hasSizeLessThanOrEqualTo(1);
            for (Throwable failure : failures) {
                assertThat(failure).isInstanceOf(CustomException.class);
                assertThat(((CustomException) failure)
                        .getBaseResponseCode())
                        .isEqualTo(ReviewErrorResponseCode
                                .REVIEW_ASSIGNMENT_DUPLICATED);
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(reviewAssignmentRepository.count()).isEqualTo(1);
    }

    private PrepareOutcome prepareAssignments() {
        try {
            return new PrepareOutcome(
                    reviewAssignmentAdminService.prepareAssignments(
                            admin.getId(),
                            contest.getPublicId(),
                            reviewRound.getId(),
                            judge.getId(),
                            new ReviewAssignmentPrepareReq(null)
                    ),
                    null
            );
        } catch (RuntimeException exception) {
            return new PrepareOutcome(List.of(), exception);
        }
    }

    @Test
    @Timeout(20)
    @DisplayName("오래된 RR 스냅샷 이후 배정이 커밋되어도 심사위원 정보 수정을 거부한다")
    void judgeUpdateSeesAssignmentCommittedAfterRepeatableReadSnapshot() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            assertThatThrownBy(() -> transaction.execute(ignored -> {
                assertThat(reviewAssignmentRepository.existsByContestJudgeId(judge.getId())).isFalse();
                try {
                    PrepareOutcome committed = executor.submit(this::prepareAssignments).get(10, TimeUnit.SECONDS);
                    assertThat(committed.failure()).isNull();
                    assertThat(committed.assignments()).hasSize(1);
                } catch (Exception exception) {
                    throw new IllegalStateException("Synthetic assignment did not commit", exception);
                }
                // This ordinary SELECT deliberately still reads the old snapshot.
                assertThat(reviewAssignmentRepository.existsByContestJudgeId(judge.getId())).isFalse();
                return contestJudgeAdminService.updateJudge(admin.getId(), contest.getPublicId(), judge.getId(),
                        new ContestJudgeUpdateReq("Must not be applied", "Must not be applied"));
            })).isInstanceOf(CustomException.class)
                    .extracting(exception -> ((CustomException) exception).getBaseResponseCode())
                    .isEqualTo(ReviewErrorResponseCode.CONTEST_JUDGE_HAS_ASSIGNMENTS);
        }
        assertThat(contestJudgeRepository.findById(judge.getId()).orElseThrow().getName()).isEqualTo(judge.getName());
        assertThat(reviewAssignmentRepository.count()).isEqualTo(1);
    }

    private void deleteTestData() {
        adminAuditLogRepository.deleteAllInBatch();
        reviewAssignmentRepository.deleteAllInBatch();
        reviewRoundEntryRepository.deleteAllInBatch();
        contestJudgeRepository.deleteAllInBatch();
        submissionRepository.deleteAllInBatch();
        teamRepository.deleteAllInBatch();
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

    private record PrepareOutcome(
            List<ReviewAssignmentRes> assignments,
            Throwable failure
    ) {
    }
}
