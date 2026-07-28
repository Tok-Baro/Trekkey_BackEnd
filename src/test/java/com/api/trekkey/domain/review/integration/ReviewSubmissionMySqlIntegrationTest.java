package com.api.trekkey.domain.review.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.ParticipationType;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.organization.entity.OrganizationStatus;
import com.api.trekkey.domain.organization.repository.OrganizationRepository;
import com.api.trekkey.domain.review.entity.ContestJudge;
import com.api.trekkey.domain.review.entity.Review;
import com.api.trekkey.domain.review.entity.ReviewAssignment;
import com.api.trekkey.domain.review.entity.ReviewAssignmentStatus;
import com.api.trekkey.domain.review.entity.ReviewCriterion;
import com.api.trekkey.domain.review.entity.ReviewRound;
import com.api.trekkey.domain.review.entity.ReviewRoundDecisionRule;
import com.api.trekkey.domain.review.entity.ReviewRoundEntry;
import com.api.trekkey.domain.review.entity.ReviewRoundEntryStatus;
import com.api.trekkey.domain.review.entity.ReviewRoundStatus;
import com.api.trekkey.domain.review.entity.ReviewRoundTargetType;
import com.api.trekkey.domain.review.entity.ReviewScoreItem;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.repository.ContestJudgeRepository;
import com.api.trekkey.domain.review.repository.ReviewAssignmentRepository;
import com.api.trekkey.domain.review.repository.ReviewCriterionRepository;
import com.api.trekkey.domain.review.repository.ReviewRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundEntryRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundRepository;
import com.api.trekkey.domain.review.repository.ReviewScoreItemRepository;
import com.api.trekkey.domain.review.publicapi.service.ReviewSubmissionService;
import com.api.trekkey.domain.review.support.ReviewLinkTokenManager;
import com.api.trekkey.domain.review.publicapi.web.dto.request.ReviewScoreReq;
import com.api.trekkey.domain.review.publicapi.web.dto.request.ReviewSubmitReq;
import com.api.trekkey.domain.review.publicapi.web.dto.response.ReviewSubmitRes;
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
import java.math.BigDecimal;
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
class ReviewSubmissionMySqlIntegrationTest {

    private static final String RAW_TOKEN = "s".repeat(43);

    @Autowired
    private ReviewSubmissionService reviewSubmissionService;

    @Autowired
    private ReviewLinkTokenManager reviewLinkTokenManager;

    @Autowired
    private ReviewScoreItemRepository reviewScoreItemRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ReviewAssignmentRepository reviewAssignmentRepository;

    @Autowired
    private ReviewRoundEntryRepository reviewRoundEntryRepository;

    @Autowired
    private ReviewCriterionRepository reviewCriterionRepository;

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

    private ReviewCriterion creativityCriterion;
    private ReviewCriterion completenessCriterion;
    private ReviewAssignment assignment;

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
                "채점 제출 통합테스트대학교"
        );
        ReflectionTestUtils.setField(
                organization,
                "status",
                OrganizationStatus.ACTIVE
        );
        organization = organizationRepository.saveAndFlush(organization);

        User admin = userRepository.saveAndFlush(User.builder()
                .organization(organization)
                .name("채점 제출 관리자")
                .email("mysql-review-submit-admin@example.com")
                .password("encoded-password")
                .role(UserRole.ADMIN)
                .memberType(MemberType.STAFF)
                .status(UserStatus.ACTIVE)
                .build());

        User participant = userRepository.saveAndFlush(User.builder()
                .organization(organization)
                .name("김참가")
                .email("mysql-review-submit-participant@example.com")
                .password("encoded-password")
                .role(UserRole.PARTICIPANT)
                .memberType(MemberType.STUDENT)
                .status(UserStatus.ACTIVE)
                .studentId("20260003")
                .major("컴퓨터공학부")
                .build());

        Contest contest = contestRepository.saveAndFlush(Contest.builder()
                .organization(organization)
                .ownerUser(admin)
                .title("채점 제출 통합 테스트 대회")
                .department("산학협력단")
                .status(ContestStatus.REVIEWING)
                .participationType(ParticipationType.TEAM)
                .awardCount(1)
                .summary("채점 제출 통합 테스트")
                .target("재학생")
                .applicationMethod("온라인")
                .benefits("상장")
                .detailHtml("<p>본문</p>")
                .build());

        LocalDateTime now = LocalDateTime.now();
        ReviewRound reviewRound = reviewRoundRepository.saveAndFlush(
                ReviewRound.builder()
                        .contest(contest)
                        .name("1차 심사")
                        .roundNo(1)
                        .status(ReviewRoundStatus.OPEN)
                        .startsAt(now.minusHours(1))
                        .endsAt(now.plusHours(2))
                        .targetType(ReviewRoundTargetType.ALL_SUBMISSIONS)
                        .decisionRule(ReviewRoundDecisionRule.MANUAL)
                        .build()
        );

        creativityCriterion = reviewCriterionRepository.saveAndFlush(
                ReviewCriterion.builder()
                        .reviewRound(reviewRound)
                        .code("creativity")
                        .label("창의성")
                        .maxScore(10)
                        .sortOrder(1)
                        .active(true)
                        .build()
        );
        completenessCriterion = reviewCriterionRepository.saveAndFlush(
                ReviewCriterion.builder()
                        .reviewRound(reviewRound)
                        .code("completeness")
                        .label("완성도")
                        .maxScore(20)
                        .sortOrder(2)
                        .active(true)
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
                        .submittedAt(now.minusDays(1))
                        .build()
        );

        ReviewRoundEntry entry = reviewRoundEntryRepository.saveAndFlush(
                ReviewRoundEntry.builder()
                        .reviewRound(reviewRound)
                        .submission(submission)
                        .status(ReviewRoundEntryStatus.IN_REVIEW)
                        .build()
        );

        ContestJudge judge = ContestJudge.builder()
                .contest(contest)
                .name("김심사")
                .roleLabel("외부 전문가")
                .build();
        judge.issueReviewLink(
                reviewLinkTokenManager.hash(RAW_TOKEN),
                now.minusMinutes(1),
                now.plusHours(1)
        );
        judge = contestJudgeRepository.saveAndFlush(judge);

        assignment = reviewAssignmentRepository.saveAndFlush(
                ReviewAssignment.builder()
                        .contestJudge(judge)
                        .reviewRoundEntry(entry)
                        .status(ReviewAssignmentStatus.ASSIGNED)
                        .assignedAt(now.minusMinutes(30))
                        .dueAt(now.plusHours(1))
                        .build()
        );
    }

    @AfterEach
    void tearDown() {
        deleteTestData();
    }

    @Test
    @DisplayName("최초 채점 제출은 Review와 기준별 점수를 원자적으로 저장한다")
    void submitReview_persistsReviewAndScoreItems() {
        ReviewSubmitRes response = reviewSubmissionService.submitReview(
                assignment.getId(),
                request("좋은 작품입니다.", "8.00", "15.50")
        );

        assertThat(response.assignmentId()).isEqualTo(assignment.getId());
        assertThat(response.totalScore())
                .isEqualByComparingTo("23.50");
        assertThat(response.comment()).isEqualTo("좋은 작품입니다.");
        assertThat(response.scores())
                .extracting(score -> score.criterionId())
                .containsExactly(
                        creativityCriterion.getId(),
                        completenessCriterion.getId()
                );
        assertThat(response.scores())
                .extracting(score -> score.score())
                .containsExactly(
                        new BigDecimal("8.00"),
                        new BigDecimal("15.50")
                );

        assertThat(reviewRepository.count()).isEqualTo(1);
        assertThat(reviewScoreItemRepository.count()).isEqualTo(2);
        assertThat(reviewScoreItemRepository.findAll())
                .extracting(ReviewScoreItem::getScore)
                .containsExactlyInAnyOrder(
                        new BigDecimal("8.00"),
                        new BigDecimal("15.50")
                );

        Review savedReview = reviewRepository.findById(
                response.reviewId()).orElseThrow();
        assertThat(savedReview.getTotalScore())
                .isEqualByComparingTo("23.50");
        assertThat(savedReview.getComment())
                .isEqualTo("좋은 작품입니다.");

        ReviewAssignment savedAssignment = reviewAssignmentRepository
                .findById(assignment.getId())
                .orElseThrow();
        assertThat(savedAssignment.getStatus())
                .isEqualTo(ReviewAssignmentStatus.COMPLETED);
        assertThat(savedAssignment.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("동일한 채점을 순차 재시도하면 기존 결과를 그대로 반환한다")
    void submitReview_sequentialSameRetryIsIdempotent() {
        ReviewSubmitRes first = reviewSubmissionService.submitReview(
                assignment.getId(),
                request("동일 재시도", "8", "15.5")
        );
        ReviewSubmitReq reorderedRetry = new ReviewSubmitReq(
                RAW_TOKEN,
                List.of(
                        new ReviewScoreReq(
                                completenessCriterion.getId(),
                                new BigDecimal("15.50")
                        ),
                        new ReviewScoreReq(
                                creativityCriterion.getId(),
                                new BigDecimal("8.0")
                        )
                ),
                "동일 재시도"
        );

        ReviewSubmitRes retried = reviewSubmissionService.submitReview(
                assignment.getId(),
                reorderedRetry
        );

        assertThat(retried.reviewId()).isEqualTo(first.reviewId());
        assertThat(retried.assignmentId())
                .isEqualTo(first.assignmentId());
        assertThat(retried.submittedAt())
                .isEqualTo(first.submittedAt());
        assertThat(reviewRepository.count()).isEqualTo(1);
        assertThat(reviewScoreItemRepository.count()).isEqualTo(2);
    }

    @Test
    @Timeout(20)
    @DisplayName("동일한 채점의 동시 재시도는 같은 Review 한 건을 반환한다")
    void submitReview_concurrentSameRequestsAreIdempotent()
            throws Exception {
        ReviewSubmitReq request =
                request("동시 동일 재시도", "8.00", "15.50");

        List<SubmitOutcome> outcomes =
                submitConcurrently(request, request);

        assertThat(outcomes).allSatisfy(outcome -> {
            assertThat(outcome.failure()).isNull();
            assertThat(outcome.response()).isNotNull();
        });
        assertThat(outcomes.stream()
                .map(outcome -> outcome.response().reviewId())
                .distinct())
                .hasSize(1);
        assertThat(reviewRepository.count()).isEqualTo(1);
        assertThat(reviewScoreItemRepository.count()).isEqualTo(2);
        assertThat(reviewAssignmentRepository
                .findById(assignment.getId())
                .orElseThrow()
                .getStatus())
                .isEqualTo(ReviewAssignmentStatus.COMPLETED);
    }

    @Test
    @Timeout(20)
    @DisplayName("서로 다른 채점을 동시에 제출하면 하나만 저장되고 다른 요청은 거부된다")
    void submitReview_concurrentDifferentRequestsRejectsOne()
            throws Exception {
        List<SubmitOutcome> outcomes = submitConcurrently(
                request("첫 번째 채점", "8.00", "15.50"),
                request("두 번째 채점", "7.00", "15.50")
        );

        List<SubmitOutcome> successes = outcomes.stream()
                .filter(outcome -> outcome.failure() == null)
                .toList();
        List<SubmitOutcome> failures = outcomes.stream()
                .filter(outcome -> outcome.failure() != null)
                .toList();

        assertThat(successes).singleElement()
                .satisfies(outcome ->
                        assertThat(outcome.response()).isNotNull());
        assertThat(failures).singleElement()
                .satisfies(outcome -> {
                    assertThat(outcome.failure())
                            .isInstanceOf(CustomException.class);
                    assertThat(((CustomException) outcome.failure())
                            .getBaseResponseCode())
                            .isEqualTo(ReviewErrorResponseCode
                                    .REVIEW_ALREADY_SUBMITTED);
                });
        assertThat(reviewRepository.count()).isEqualTo(1);
        assertThat(reviewScoreItemRepository.count()).isEqualTo(2);
        assertThat(reviewRepository.findAll())
                .singleElement()
                .extracting(Review::getId)
                .isEqualTo(successes.getFirst().response().reviewId());
    }

    private ReviewSubmitReq request(
            String comment,
            String creativity,
            String completeness
    ) {
        return new ReviewSubmitReq(
                RAW_TOKEN,
                List.of(
                        new ReviewScoreReq(
                                creativityCriterion.getId(),
                                new BigDecimal(creativity)
                        ),
                        new ReviewScoreReq(
                                completenessCriterion.getId(),
                                new BigDecimal(completeness)
                        )
                ),
                comment
        );
    }

    private List<SubmitOutcome> submitConcurrently(
            ReviewSubmitReq firstRequest,
            ReviewSubmitReq secondRequest
    ) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<SubmitOutcome>> futures = new ArrayList<>();

        try {
            futures.add(executor.submit(() ->
                    submitWhenStarted(ready, start, firstRequest)));
            futures.add(executor.submit(() ->
                    submitWhenStarted(ready, start, secondRequest)));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<SubmitOutcome> outcomes = new ArrayList<>();
            for (Future<SubmitOutcome> future : futures) {
                outcomes.add(future.get(15, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private SubmitOutcome submitWhenStarted(
            CountDownLatch ready,
            CountDownLatch start,
            ReviewSubmitReq request
    ) {
        ready.countDown();
        await(start);
        try {
            return new SubmitOutcome(
                    reviewSubmissionService.submitReview(
                            assignment.getId(),
                            request
                    ),
                    null
            );
        } catch (RuntimeException exception) {
            return new SubmitOutcome(null, exception);
        }
    }

    private void deleteTestData() {
        reviewScoreItemRepository.deleteAllInBatch();
        reviewRepository.deleteAllInBatch();
        reviewAssignmentRepository.deleteAllInBatch();
        reviewRoundEntryRepository.deleteAllInBatch();
        reviewCriterionRepository.deleteAllInBatch();
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

    private record SubmitOutcome(
            ReviewSubmitRes response,
            RuntimeException failure
    ) {
    }
}
