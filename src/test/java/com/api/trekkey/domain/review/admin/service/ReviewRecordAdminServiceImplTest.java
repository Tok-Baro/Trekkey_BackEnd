package com.api.trekkey.domain.review.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.ParticipationType;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.review.admin.web.dto.response.ReviewRecordRes;
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
import com.api.trekkey.domain.review.repository.ReviewRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundRepository;
import com.api.trekkey.domain.review.repository.ReviewScoreItemRepository;
import com.api.trekkey.domain.submission.entity.Submission;
import com.api.trekkey.domain.submission.entity.SubmissionStatus;
import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.domain.team.entity.TeamStatus;
import com.api.trekkey.domain.user.entity.MemberType;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReviewRecordAdminServiceImplTest {

    private static final Long ADMIN_ID = 10L;
    private static final Long ORGANIZATION_ID = 20L;
    private static final Long CONTEST_ID = 30L;
    private static final Long ROUND_ID = 40L;
    private static final Long ENTRY_ID = 50L;
    private static final Long JUDGE_ID = 60L;
    private static final Long ASSIGNMENT_ID = 70L;
    private static final Long REVIEW_ID = 80L;
    private static final String CONTEST_PUBLIC_ID = "contest-public-id";
    private static final LocalDateTime SUBMITTED_AT =
            LocalDateTime.of(2026, 8, 4, 15, 30);

    @Mock
    private UserRepository userRepository;

    @Mock
    private ContestRepository contestRepository;

    @Mock
    private ReviewRoundRepository reviewRoundRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewScoreItemRepository reviewScoreItemRepository;

    private ReviewRecordAdminServiceImpl service;
    private Organization organization;
    private User admin;
    private Contest contest;
    private ReviewRound round;

    @BeforeEach
    void setUp() {
        service = new ReviewRecordAdminServiceImpl(
                userRepository,
                contestRepository,
                reviewRoundRepository,
                reviewRepository,
                reviewScoreItemRepository
        );

        organization = organization(ORGANIZATION_ID);
        admin = admin(organization);
        contest = contest(CONTEST_ID, organization);
        round = round(ROUND_ID, contest);
    }

    @Test
    @DisplayName("라운드의 심사 기록과 기준별 점수를 함께 조회한다")
    void getReviews_mapsReviewRecordsAndScores() {
        Review review = review(round);
        ReviewCriterion creativity = criterion(
                90L,
                round,
                "creativity",
                "창의성",
                50,
                1
        );
        ReviewCriterion completeness = criterion(
                91L,
                round,
                "completeness",
                "완성도",
                50,
                2
        );
        ReviewScoreItem firstScore = scoreItem(
                100L,
                review,
                creativity,
                "45.50"
        );
        ReviewScoreItem secondScore = scoreItem(
                101L,
                review,
                completeness,
                "47.00"
        );
        givenOwnedScope();
        given(reviewRepository.findAllWithDetailsByReviewRoundId(ROUND_ID))
                .willReturn(List.of(review));
        given(reviewScoreItemRepository.findAllWithCriterionByReviewIdIn(
                List.of(REVIEW_ID)))
                .willReturn(List.of(firstScore, secondScore));

        List<ReviewRecordRes> response = service.getReviews(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                ROUND_ID
        );

        assertThat(response).singleElement().satisfies(record -> {
            assertThat(record.reviewId()).isEqualTo(REVIEW_ID);
            assertThat(record.assignmentId()).isEqualTo(ASSIGNMENT_ID);
            assertThat(record.reviewRoundId()).isEqualTo(ROUND_ID);
            assertThat(record.reviewRoundEntryId()).isEqualTo(ENTRY_ID);
            assertThat(record.judgeId()).isEqualTo(JUDGE_ID);
            assertThat(record.judgeName()).isEqualTo("김심사");
            assertThat(record.judgeRoleLabel()).isEqualTo("외부 전문가");
            assertThat(record.submissionPublicId())
                    .isEqualTo("submission-public-id");
            assertThat(record.submissionTitle()).isEqualTo("AI 캠퍼스");
            assertThat(record.teamName()).isEqualTo("트랙키 팀");
            assertThat(record.totalScore())
                    .isEqualByComparingTo("92.50");
            assertThat(record.comment()).isEqualTo("좋은 작품입니다.");
            assertThat(record.submittedAt()).isEqualTo(SUBMITTED_AT);
            assertThat(record.scores())
                    .extracting(score -> score.code())
                    .containsExactly("creativity", "completeness");
            assertThat(record.scores())
                    .extracting(score -> score.score())
                    .containsExactly(
                            new BigDecimal("45.50"),
                            new BigDecimal("47.00")
                    );
        });

        verify(reviewRepository)
                .findAllWithDetailsByReviewRoundId(ROUND_ID);
        verify(reviewScoreItemRepository)
                .findAllWithCriterionByReviewIdIn(List.of(REVIEW_ID));
    }

    @Test
    @DisplayName("심사 기록이 없으면 빈 목록을 반환하고 점수 항목을 조회하지 않는다")
    void getReviews_returnsEmptyWithoutScoreItemQuery() {
        givenOwnedScope();
        given(reviewRepository.findAllWithDetailsByReviewRoundId(ROUND_ID))
                .willReturn(List.of());

        List<ReviewRecordRes> response = service.getReviews(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                ROUND_ID
        );

        assertThat(response).isEmpty();
        verifyNoInteractions(reviewScoreItemRepository);
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 심사 기록 조회를 거부한다")
    void getReviews_rejectsMissingUser() {
        given(userRepository.findById(ADMIN_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getReviews(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                ROUND_ID
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception ->
                        ((CustomException) exception).getBaseResponseCode())
                .isEqualTo(UserErrorResponseCode.USER_NOT_FOUND);

        verifyNoInteractions(
                contestRepository,
                reviewRoundRepository,
                reviewRepository,
                reviewScoreItemRepository
        );
    }

    @Test
    @DisplayName("다른 조직 대회의 심사 기록 조회를 거부한다")
    void getReviews_rejectsContestFromAnotherOrganization() {
        Organization anotherOrganization = organization(21L);
        Contest anotherContest = contest(CONTEST_ID, anotherOrganization);
        given(userRepository.findById(ADMIN_ID))
                .willReturn(Optional.of(admin));
        given(contestRepository.findByPublicId(CONTEST_PUBLIC_ID))
                .willReturn(Optional.of(anotherContest));

        assertThatThrownBy(() -> service.getReviews(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                ROUND_ID
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception ->
                        ((CustomException) exception).getBaseResponseCode())
                .isEqualTo(ContestErrorResponseCode.CONTEST_FORBIDDEN);

        verifyNoInteractions(
                reviewRoundRepository,
                reviewRepository,
                reviewScoreItemRepository
        );
    }

    @Test
    @DisplayName("요청한 대회에 속하지 않은 심사 라운드는 찾을 수 없다")
    void getReviews_rejectsRoundFromAnotherContest() {
        Contest anotherContest = contest(31L, organization);
        ReviewRound anotherRound = round(ROUND_ID, anotherContest);
        given(userRepository.findById(ADMIN_ID))
                .willReturn(Optional.of(admin));
        given(contestRepository.findByPublicId(CONTEST_PUBLIC_ID))
                .willReturn(Optional.of(contest));
        given(reviewRoundRepository.findById(ROUND_ID))
                .willReturn(Optional.of(anotherRound));

        assertThatThrownBy(() -> service.getReviews(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                ROUND_ID
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception ->
                        ((CustomException) exception).getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode.REVIEW_ROUND_NOT_FOUND);

        verifyNoInteractions(reviewRepository, reviewScoreItemRepository);
    }

    private void givenOwnedScope() {
        given(userRepository.findById(ADMIN_ID))
                .willReturn(Optional.of(admin));
        given(contestRepository.findByPublicId(CONTEST_PUBLIC_ID))
                .willReturn(Optional.of(contest));
        given(reviewRoundRepository.findById(ROUND_ID))
                .willReturn(Optional.of(round));
    }

    private Organization organization(Long id) {
        Organization found = org.mockito.Mockito.mock(Organization.class);
        org.mockito.Mockito.lenient()
                .when(found.getId())
                .thenReturn(id);
        return found;
    }

    private User admin(Organization foundOrganization) {
        User found = User.builder()
                .organization(foundOrganization)
                .name("관리자")
                .email("admin@example.com")
                .password("encoded")
                .role(UserRole.ADMIN)
                .memberType(MemberType.STAFF)
                .status(UserStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(found, "id", ADMIN_ID);
        return found;
    }

    private Contest contest(Long id, Organization foundOrganization) {
        Contest found = Contest.builder()
                .publicId(CONTEST_PUBLIC_ID)
                .organization(foundOrganization)
                .ownerUser(admin)
                .title("AI 공모전")
                .department("교무처")
                .status(ContestStatus.REVIEWING)
                .participationType(ParticipationType.TEAM)
                .awardCount(1)
                .summary("AI 공모전")
                .target("재학생")
                .applicationMethod("온라인")
                .benefits("상장")
                .detailHtml("<p>본문</p>")
                .build();
        ReflectionTestUtils.setField(found, "id", id);
        return found;
    }

    private ReviewRound round(Long id, Contest foundContest) {
        ReviewRound found = ReviewRound.builder()
                .contest(foundContest)
                .roundNo(1)
                .name("예선 심사")
                .status(ReviewRoundStatus.OPEN)
                .startsAt(SUBMITTED_AT.minusHours(2))
                .endsAt(SUBMITTED_AT.plusHours(2))
                .targetType(ReviewRoundTargetType.ALL_SUBMISSIONS)
                .decisionRule(ReviewRoundDecisionRule.TOP_N)
                .selectCount(10)
                .build();
        ReflectionTestUtils.setField(found, "id", id);
        return found;
    }

    private Review review(ReviewRound foundRound) {
        Team team = Team.builder()
                .contest(contest)
                .leaderUser(admin)
                .name("트랙키 팀")
                .leaderName("김대표")
                .major("컴퓨터공학부")
                .memberCount(2)
                .status(TeamStatus.APPROVED)
                .contactEmail("leader@example.com")
                .phone("010-1234-5678")
                .motivation("학교 문제를 해결합니다.")
                .build();
        ReflectionTestUtils.setField(team, "id", 110L);
        Submission submission = Submission.builder()
                .publicId("submission-public-id")
                .team(team)
                .title("AI 캠퍼스")
                .status(SubmissionStatus.SUBMITTED)
                .submittedAt(SUBMITTED_AT.minusDays(1))
                .build();
        ReflectionTestUtils.setField(submission, "id", 120L);
        ReviewRoundEntry entry = ReviewRoundEntry.builder()
                .reviewRound(foundRound)
                .submission(submission)
                .status(ReviewRoundEntryStatus.IN_REVIEW)
                .build();
        ReflectionTestUtils.setField(entry, "id", ENTRY_ID);
        ContestJudge judge = ContestJudge.builder()
                .contest(contest)
                .name("김심사")
                .roleLabel("외부 전문가")
                .build();
        ReflectionTestUtils.setField(judge, "id", JUDGE_ID);
        ReviewAssignment assignment = ReviewAssignment.builder()
                .contestJudge(judge)
                .reviewRoundEntry(entry)
                .status(ReviewAssignmentStatus.COMPLETED)
                .assignedAt(SUBMITTED_AT.minusDays(1))
                .completedAt(SUBMITTED_AT)
                .build();
        ReflectionTestUtils.setField(
                assignment,
                "id",
                ASSIGNMENT_ID
        );
        Review found = Review.builder()
                .assignment(assignment)
                .totalScore(new BigDecimal("92.50"))
                .comment("좋은 작품입니다.")
                .submittedAt(SUBMITTED_AT)
                .build();
        ReflectionTestUtils.setField(found, "id", REVIEW_ID);
        return found;
    }

    private ReviewCriterion criterion(
            Long id,
            ReviewRound foundRound,
            String code,
            String label,
            int maxScore,
            int sortOrder) {
        ReviewCriterion found = ReviewCriterion.builder()
                .reviewRound(foundRound)
                .code(code)
                .label(label)
                .maxScore(maxScore)
                .sortOrder(sortOrder)
                .active(true)
                .build();
        ReflectionTestUtils.setField(found, "id", id);
        return found;
    }

    private ReviewScoreItem scoreItem(
            Long id,
            Review foundReview,
            ReviewCriterion criterion,
            String score) {
        ReviewScoreItem item = ReviewScoreItem.builder()
                .review(foundReview)
                .reviewCriterion(criterion)
                .score(new BigDecimal(score))
                .build();
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }
}
