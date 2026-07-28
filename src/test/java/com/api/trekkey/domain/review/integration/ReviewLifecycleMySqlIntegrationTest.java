package com.api.trekkey.domain.review.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.api.trekkey.domain.audit.repository.AdminAuditLogRepository;
import com.api.trekkey.domain.award.admin.service.AwardAdminService;
import com.api.trekkey.domain.award.admin.web.dto.AwardRes;
import com.api.trekkey.domain.award.entity.Award;
import com.api.trekkey.domain.award.entity.AwardStatus;
import com.api.trekkey.domain.award.repository.AwardRepository;
import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.ParticipationType;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.credential.integration.WorkCredentialIssuer;
import com.api.trekkey.domain.credential.integration.AwardCredentialIssuer;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.organization.entity.OrganizationStatus;
import com.api.trekkey.domain.organization.repository.OrganizationRepository;
import com.api.trekkey.domain.review.admin.service.ReviewAssignmentAdminService;
import com.api.trekkey.domain.review.admin.service.ReviewRoundAdminService;
import com.api.trekkey.domain.review.admin.service.ReviewRoundEntryAdminService;
import com.api.trekkey.domain.review.admin.service.ReviewRoundFinalizationAdminService;
import com.api.trekkey.domain.review.admin.web.dto.request.ReviewAssignmentPrepareReq;
import com.api.trekkey.domain.review.admin.web.dto.request.ReviewManualDecisionReq;
import com.api.trekkey.domain.review.admin.web.dto.request.ReviewRoundEntryPrepareReq;
import com.api.trekkey.domain.review.admin.web.dto.request.ReviewRoundFinalizeReq;
import com.api.trekkey.domain.review.admin.web.dto.response.ReviewAssignmentRes;
import com.api.trekkey.domain.review.admin.web.dto.response.ReviewRoundEntryRes;
import com.api.trekkey.domain.review.admin.web.dto.response.ReviewRoundFinalizeRes;
import com.api.trekkey.domain.review.entity.ContestJudge;
import com.api.trekkey.domain.review.entity.ReviewAssignmentStatus;
import com.api.trekkey.domain.review.entity.ReviewCriterion;
import com.api.trekkey.domain.review.entity.ReviewRound;
import com.api.trekkey.domain.review.entity.ReviewRoundDecisionRule;
import com.api.trekkey.domain.review.entity.ReviewRoundEntry;
import com.api.trekkey.domain.review.entity.ReviewRoundEntryStatus;
import com.api.trekkey.domain.review.entity.ReviewRoundStatus;
import com.api.trekkey.domain.review.entity.ReviewRoundTargetType;
import com.api.trekkey.domain.review.publicapi.service.ReviewSubmissionService;
import com.api.trekkey.domain.review.publicapi.web.dto.request.ReviewScoreReq;
import com.api.trekkey.domain.review.publicapi.web.dto.request.ReviewSubmitReq;
import com.api.trekkey.domain.review.repository.ContestJudgeRepository;
import com.api.trekkey.domain.review.repository.ReviewAssignmentRepository;
import com.api.trekkey.domain.review.repository.ReviewCriterionRepository;
import com.api.trekkey.domain.review.repository.ReviewRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundEntryRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundRepository;
import com.api.trekkey.domain.review.repository.ReviewScoreItemRepository;
import com.api.trekkey.domain.review.support.ReviewLinkTokenManager;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
class ReviewLifecycleMySqlIntegrationTest {

    private static final String RAW_TOKEN = "l".repeat(43);

    @Autowired
    private ReviewRoundEntryAdminService reviewRoundEntryAdminService;

    @Autowired
    private ReviewAssignmentAdminService reviewAssignmentAdminService;

    @Autowired
    private ReviewRoundAdminService reviewRoundAdminService;

    @Autowired
    private ReviewSubmissionService reviewSubmissionService;

    @Autowired
    private ReviewRoundFinalizationAdminService finalizationAdminService;

    @Autowired
    private AwardAdminService awardAdminService;

    @Autowired
    private ReviewLinkTokenManager reviewLinkTokenManager;

    @Autowired
    private AwardRepository awardRepository;

    @Autowired
    private ReviewScoreItemRepository reviewScoreItemRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ReviewAssignmentRepository reviewAssignmentRepository;

    @Autowired
    private ReviewRoundEntryRepository reviewRoundEntryRepository;

    @Autowired
    private ContestJudgeRepository contestJudgeRepository;

    @Autowired
    private ReviewCriterionRepository reviewCriterionRepository;

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

    @MockitoBean
    private WorkCredentialIssuer workCredentialIssuer;

    @MockitoBean
    private AwardCredentialIssuer awardCredentialIssuer;

    private User admin;
    private Contest contest;
    private ReviewRound reviewRound;
    private ReviewCriterion creativityCriterion;
    private ReviewCriterion feasibilityCriterion;
    private ContestJudge judge;
    private Submission highScoreSubmission;

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
                "심사 전체 흐름 통합테스트대학교"
        );
        ReflectionTestUtils.setField(
                organization,
                "status",
                OrganizationStatus.ACTIVE
        );
        organization = organizationRepository.saveAndFlush(organization);

        admin = userRepository.saveAndFlush(User.builder()
                .organization(organization)
                .name("심사 전체 흐름 관리자")
                .email("mysql-review-lifecycle-admin@example.com")
                .password("encoded-password")
                .role(UserRole.ADMIN)
                .memberType(MemberType.STAFF)
                .status(UserStatus.ACTIVE)
                .build());

        User firstParticipant = saveParticipant(
                organization,
                "고득점 참가자",
                "mysql-review-lifecycle-high@example.com",
                "20261001"
        );
        User secondParticipant = saveParticipant(
                organization,
                "저득점 참가자",
                "mysql-review-lifecycle-low@example.com",
                "20261002"
        );

        contest = contestRepository.saveAndFlush(Contest.builder()
                .organization(organization)
                .ownerUser(admin)
                .title("심사 전체 흐름 통합 테스트 대회")
                .department("산학협력단")
                .status(ContestStatus.APPLICATION_OPEN)
                .participationType(ParticipationType.TEAM)
                .awardCount(1)
                .summary("심사 전체 흐름 통합 테스트")
                .target("재학생")
                .applicationMethod("온라인")
                .benefits("상장")
                .detailHtml("<p>본문</p>")
                .build());

        LocalDateTime now = LocalDateTime.now();
        reviewRound = reviewRoundRepository.saveAndFlush(
                ReviewRound.builder()
                        .contest(contest)
                        .roundNo(1)
                        .name("최종 심사")
                        .status(ReviewRoundStatus.PREPARING)
                        .startsAt(now.minusHours(1))
                        .endsAt(now.plusHours(2))
                        .targetType(ReviewRoundTargetType.ALL_SUBMISSIONS)
                        .decisionRule(ReviewRoundDecisionRule.TOP_N)
                        .selectCount(1)
                        .build()
        );

        creativityCriterion = reviewCriterionRepository.saveAndFlush(
                ReviewCriterion.builder()
                        .reviewRound(reviewRound)
                        .code("creativity")
                        .label("창의성")
                        .maxScore(60)
                        .sortOrder(1)
                        .active(true)
                        .build()
        );
        feasibilityCriterion = reviewCriterionRepository.saveAndFlush(
                ReviewCriterion.builder()
                        .reviewRound(reviewRound)
                        .code("feasibility")
                        .label("실현 가능성")
                        .maxScore(40)
                        .sortOrder(2)
                        .active(true)
                        .build()
        );

        Team highScoreTeam = saveTeam(
                firstParticipant,
                "고득점 팀",
                "high-score-team@example.com"
        );
        Team lowScoreTeam = saveTeam(
                secondParticipant,
                "저득점 팀",
                "low-score-team@example.com"
        );
        highScoreSubmission = saveSubmission(
                highScoreTeam,
                "캠퍼스 안전 플랫폼",
                now.minusDays(1)
        );
        saveSubmission(
                lowScoreTeam,
                "캠퍼스 소식 게시판",
                now.minusDays(1)
        );

        judge = ContestJudge.builder()
                .contest(contest)
                .name("김심사")
                .roleLabel("외부 전문가")
                .build();
        judge.issueReviewLink(
                reviewLinkTokenManager.hash(RAW_TOKEN),
                now.minusMinutes(1),
                now.plusHours(2)
        );
        judge = contestJudgeRepository.saveAndFlush(judge);
    }

    @AfterEach
    void tearDown() {
        deleteTestData();
    }

    @Test
    @DisplayName("대상 준비부터 채점 확정과 수상 후보 산출까지 전체 흐름이 이어진다")
    void reviewLifecycle_createsAwardCandidateFromFinalizedTopEntry() {
        List<ReviewRoundEntryRes> entries =
                reviewRoundEntryAdminService.prepareEntries(
                        admin.getId(),
                        contest.getPublicId(),
                        reviewRound.getId()
                );
        assertThat(entries).hasSize(2);
        assertThat(entries)
                .allMatch(entry ->
                        entry.status() == ReviewRoundEntryStatus.ELIGIBLE);

        List<ReviewAssignmentRes> assignments =
                reviewAssignmentAdminService.prepareAssignments(
                        admin.getId(),
                        contest.getPublicId(),
                        reviewRound.getId(),
                        judge.getId(),
                        new ReviewAssignmentPrepareReq(null)
                );
        assertThat(assignments).hasSize(2);
        assertThat(assignments)
                .allMatch(assignment ->
                        assignment.status()
                                == ReviewAssignmentStatus.ASSIGNED);

        assertThat(reviewRoundAdminService.openRound(
                admin.getId(),
                contest.getPublicId(),
                reviewRound.getId()
        ).status()).isEqualTo(ReviewRoundStatus.OPEN);
        assertThat(contestRepository.findById(contest.getId())
                .orElseThrow()
                .getStatus()).isEqualTo(ContestStatus.REVIEWING);

        for (ReviewAssignmentRes assignment : assignments) {
            boolean highScore = assignment.submissionPublicId()
                    .equals(highScoreSubmission.getPublicId());
            reviewSubmissionService.submitReview(
                    assignment.id(),
                    reviewRequest(
                            highScore ? "55.00" : "30.00",
                            highScore ? "35.00" : "20.00",
                            highScore
                                    ? "수상 후보로 적합합니다."
                                    : "보완이 필요합니다."
                    )
            );
        }

        ReviewRoundFinalizeRes finalized =
                finalizationAdminService.finalizeRound(
                        admin.getId(),
                        contest.getPublicId(),
                        reviewRound.getId(),
                        null
                );

        assertThat(finalized.status())
                .isEqualTo(ReviewRoundStatus.FINALIZED);
        assertThat(finalized.finalizedAt()).isNotNull();
        assertThat(finalized.entries())
                .extracting(
                        ReviewRoundEntryRes::submissionPublicId,
                        ReviewRoundEntryRes::finalScore,
                        ReviewRoundEntryRes::rankNo,
                        ReviewRoundEntryRes::status)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                highScoreSubmission.getPublicId(),
                                new BigDecimal("90.00"),
                                1,
                                ReviewRoundEntryStatus.SELECTED),
                        org.assertj.core.groups.Tuple.tuple(
                                assignments.stream()
                                        .map(ReviewAssignmentRes
                                                ::submissionPublicId)
                                        .filter(publicId -> !publicId.equals(
                                                highScoreSubmission
                                                        .getPublicId()))
                                        .findFirst()
                                        .orElseThrow(),
                                new BigDecimal("50.00"),
                                2,
                                ReviewRoundEntryStatus.NOT_SELECTED)
                );

        List<AwardRes> awards = awardAdminService.calculateAwards(
                admin.getId(),
                reviewRound.getId()
        );

        assertThat(awards).singleElement().satisfies(award -> {
            assertThat(award.status()).isEqualTo(AwardStatus.CANDIDATE);
            assertThat(award.awardRankNo()).isEqualTo(1);
            assertThat(award.teamName()).isEqualTo("고득점 팀");
            assertThat(award.submissionTitle())
                    .isEqualTo("캠퍼스 안전 플랫폼");
            assertThat(award.finalScore())
                    .isEqualByComparingTo("90.00");
        });
        assertThat(reviewRepository.count()).isEqualTo(2);
        assertThat(reviewScoreItemRepository.count()).isEqualTo(4);
        assertThat(reviewAssignmentRepository.findAll())
                .allMatch(assignment ->
                        assignment.getStatus()
                                == ReviewAssignmentStatus.COMPLETED);
        assertThat(awardRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("심사 없는 수동 라운드는 명시 순위로 확정하고 수상 후보까지 만든다")
    void manualLifecycle_finalizesWithoutScoresAndCreatesAwardCandidate() {
        reviewCriterionRepository.deleteAllInBatch();
        reviewRound.updateConfiguration(
                "수동 최종 선정",
                1,
                reviewRound.getStartsAt(),
                reviewRound.getEndsAt(),
                ReviewRoundTargetType.MANUAL,
                ReviewRoundDecisionRule.MANUAL,
                null,
                null);
        reviewRound = reviewRoundRepository.saveAndFlush(reviewRound);

        List<Submission> submissions = submissionRepository.findAll();
        List<ReviewRoundEntryRes> entries =
                reviewRoundEntryAdminService.prepareEntries(
                        admin.getId(),
                        contest.getPublicId(),
                        reviewRound.getId(),
                        new ReviewRoundEntryPrepareReq(
                                submissions.stream()
                                        .map(Submission::getPublicId)
                                        .toList())
                );
        assertThat(entries).hasSize(2);

        assertThat(reviewRoundAdminService.openRound(
                admin.getId(),
                contest.getPublicId(),
                reviewRound.getId()
        ).status()).isEqualTo(ReviewRoundStatus.OPEN);

        ReviewRoundEntryRes selected = entries.stream()
                .filter(entry -> entry.submissionPublicId()
                        .equals(highScoreSubmission.getPublicId()))
                .findFirst()
                .orElseThrow();
        ReviewRoundEntryRes notSelected = entries.stream()
                .filter(entry -> !entry.id().equals(selected.id()))
                .findFirst()
                .orElseThrow();
        ReviewRoundFinalizeRes finalized =
                finalizationAdminService.finalizeRound(
                        admin.getId(),
                        contest.getPublicId(),
                        reviewRound.getId(),
                        new ReviewRoundFinalizeReq(List.of(
                                new ReviewManualDecisionReq(
                                        selected.id(),
                                        ReviewRoundEntryStatus.SELECTED,
                                        "위원회 최종 선정",
                                        1),
                                new ReviewManualDecisionReq(
                                        notSelected.id(),
                                        ReviewRoundEntryStatus.NOT_SELECTED,
                                        "위원회 미선정",
                                        2)
                        ))
                );

        assertThat(finalized.status())
                .isEqualTo(ReviewRoundStatus.FINALIZED);
        assertThat(finalized.entries())
                .extracting(
                        ReviewRoundEntryRes::finalScore,
                        ReviewRoundEntryRes::rankNo)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(null, 1),
                        org.assertj.core.groups.Tuple.tuple(null, 2));
        assertThat(reviewAssignmentRepository.count()).isZero();
        assertThat(reviewRepository.count()).isZero();
        assertThat(reviewScoreItemRepository.count()).isZero();

        List<AwardRes> awards = awardAdminService.calculateAwards(
                admin.getId(),
                reviewRound.getId());
        assertThat(awards).singleElement().satisfies(award -> {
            assertThat(award.status()).isEqualTo(AwardStatus.CANDIDATE);
            assertThat(award.teamName()).isEqualTo("고득점 팀");
            assertThat(award.finalScore()).isNull();
        });
    }

    @Test
    @DisplayName("수상 Credential 발급 실패 시 수상과 대회 상태를 함께 롤백한다")
    void confirmAwards_rollsBackWhenCredentialIssuanceFails() {
        reviewRound.open();
        reviewRound.finalizeAt(LocalDateTime.now());
        reviewRound = reviewRoundRepository.saveAndFlush(reviewRound);
        ReviewRoundEntry entry = ReviewRoundEntry.builder()
                .reviewRound(reviewRound)
                .submission(highScoreSubmission)
                .status(ReviewRoundEntryStatus.IN_REVIEW)
                .build();
        entry.finalizeByRule(
                new BigDecimal("90.00"),
                1,
                ReviewRoundEntryStatus.SELECTED,
                LocalDateTime.now());
        entry = reviewRoundEntryRepository.saveAndFlush(entry);
        Award candidate = awardRepository.saveAndFlush(Award.builder()
                .reviewRoundEntry(entry)
                .team(highScoreSubmission.getTeam())
                .awardRankNo(1)
                .prize("대상")
                .status(AwardStatus.CANDIDATE)
                .certificateNo("2026-C"
                        + contest.getId()
                        + "-001")
                .build());
        given(awardCredentialIssuer.issueForConfirmedAward(
                any(Award.class)))
                .willThrow(new IllegalStateException(
                        "credential issuance failed"));

        assertThatThrownBy(() -> awardAdminService.confirmAwards(
                admin.getId(),
                contest.getPublicId()
        )).isInstanceOf(IllegalStateException.class);

        Award storedAward = awardRepository.findById(candidate.getId())
                .orElseThrow();
        Contest storedContest = contestRepository.findById(contest.getId())
                .orElseThrow();
        assertThat(storedAward.getStatus())
                .isEqualTo(AwardStatus.CANDIDATE);
        assertThat(storedAward.getConfirmedAt()).isNull();
        assertThat(storedContest.getStatus())
                .isEqualTo(ContestStatus.APPLICATION_OPEN);
    }

    private User saveParticipant(
            Organization organization,
            String name,
            String email,
            String studentId
    ) {
        return userRepository.saveAndFlush(User.builder()
                .organization(organization)
                .name(name)
                .email(email)
                .password("encoded-password")
                .role(UserRole.PARTICIPANT)
                .memberType(MemberType.STUDENT)
                .status(UserStatus.ACTIVE)
                .studentId(studentId)
                .major("컴퓨터공학부")
                .build());
    }

    private Team saveTeam(
            User leader,
            String name,
            String contactEmail
    ) {
        Team team = Team.builder()
                .contest(contest)
                .leaderUser(leader)
                .name(name)
                .leaderName(leader.getName())
                .major("컴퓨터공학부")
                .memberCount(1)
                .status(TeamStatus.APPROVED)
                .contactEmail(contactEmail)
                .phone("010-1234-5678")
                .motivation("학교 문제를 해결합니다.")
                .build();
        team.finalizeParticipation(LocalDateTime.now().minusDays(2));
        return teamRepository.saveAndFlush(team);
    }

    private Submission saveSubmission(
            Team team,
            String title,
            LocalDateTime submittedAt
    ) {
        return submissionRepository.saveAndFlush(
                Submission.builder()
                        .team(team)
                        .title(title)
                        .status(SubmissionStatus.SUBMITTED)
                        .submittedAt(submittedAt)
                        .build()
        );
    }

    private ReviewSubmitReq reviewRequest(
            String creativityScore,
            String feasibilityScore,
            String comment
    ) {
        return new ReviewSubmitReq(
                RAW_TOKEN,
                List.of(
                        new ReviewScoreReq(
                                creativityCriterion.getId(),
                                new BigDecimal(creativityScore)
                        ),
                        new ReviewScoreReq(
                                feasibilityCriterion.getId(),
                                new BigDecimal(feasibilityScore)
                        )
                ),
                comment
        );
    }

    private void deleteTestData() {
        adminAuditLogRepository.deleteAllInBatch();
        awardRepository.deleteAllInBatch();
        reviewScoreItemRepository.deleteAllInBatch();
        reviewRepository.deleteAllInBatch();
        reviewAssignmentRepository.deleteAllInBatch();
        reviewRoundEntryRepository.deleteAllInBatch();
        contestJudgeRepository.deleteAllInBatch();
        submissionRepository.deleteAllInBatch();
        teamRepository.deleteAllInBatch();
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
}
