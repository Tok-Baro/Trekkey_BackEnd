package com.api.trekkey.domain.review.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.api.trekkey.domain.audit.support.AdminAuditLogger;
import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.review.admin.web.dto.request.ReviewManualDecisionReq;
import com.api.trekkey.domain.review.admin.web.dto.request.ReviewRoundFinalizeReq;
import com.api.trekkey.domain.review.admin.web.dto.response.ReviewRoundFinalizeRes;
import com.api.trekkey.domain.review.entity.Review;
import com.api.trekkey.domain.review.entity.ReviewAssignment;
import com.api.trekkey.domain.review.entity.ReviewAssignmentStatus;
import com.api.trekkey.domain.review.entity.ReviewDecisionType;
import com.api.trekkey.domain.review.entity.ReviewRound;
import com.api.trekkey.domain.review.entity.ReviewRoundDecisionRule;
import com.api.trekkey.domain.review.entity.ReviewRoundEntry;
import com.api.trekkey.domain.review.entity.ReviewRoundEntryStatus;
import com.api.trekkey.domain.review.entity.ReviewRoundStatus;
import com.api.trekkey.domain.review.entity.ReviewRoundTargetType;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.repository.ReviewAssignmentRepository;
import com.api.trekkey.domain.review.repository.ReviewRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundEntryRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundRepository;
import com.api.trekkey.domain.submission.entity.Submission;
import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.domain.user.entity.MemberType;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewRoundFinalizationAdminServiceImplTest {

    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 7, 28, 15, 0);

    @Mock
    private UserRepository userRepository;
    @Mock
    private ContestRepository contestRepository;
    @Mock
    private ReviewRoundRepository reviewRoundRepository;
    @Mock
    private ReviewRoundEntryRepository reviewRoundEntryRepository;
    @Mock
    private ReviewAssignmentRepository reviewAssignmentRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private AdminAuditLogger adminAuditLogger;
    @Mock
    private Organization organization;

    private ReviewRoundFinalizationAdminServiceImpl service;
    private User admin;
    private Contest contest;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-28T06:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        service = new ReviewRoundFinalizationAdminServiceImpl(
                userRepository,
                contestRepository,
                reviewRoundRepository,
                reviewRoundEntryRepository,
                reviewAssignmentRepository,
                reviewRepository,
                adminAuditLogger,
                clock
        );
        given(organization.getId()).willReturn(1L);
        admin = User.builder()
                .id(10L)
                .organization(organization)
                .name("관리자")
                .email("admin@example.com")
                .password("encoded")
                .role(UserRole.ADMIN)
                .memberType(MemberType.STAFF)
                .status(UserStatus.ACTIVE)
                .build();
        contest = Contest.builder()
                .id(20L)
                .publicId("contest-public-id")
                .organization(organization)
                .ownerUser(admin)
                .title("AI 공모전")
                .build();
    }

    @Test
    @DisplayName("완료된 심사 점수의 평균으로 순위와 TOP_N 결과를 확정한다")
    void finalizeRound_appliesTopNRule() {
        ReviewRound round = round(
                ReviewRoundDecisionRule.TOP_N,
                1,
                null
        );
        ReviewRoundEntry first = entry(100L, round, "submission-a", "A팀");
        ReviewRoundEntry second = entry(101L, round, "submission-b", "B팀");
        ReviewAssignment firstAssignment = completedAssignment(1000L, first);
        ReviewAssignment secondAssignment = completedAssignment(1001L, second);
        stubCommon(round, List.of(first, second));
        given(reviewAssignmentRepository
                .findAllForShareByReviewRoundEntryIdInOrderByEntryIdAscIdAsc(
                        List.of(100L, 101L)))
                .willReturn(List.of(firstAssignment, secondAssignment));
        given(reviewRepository.findAllByAssignmentIdIn(
                List.of(1000L, 1001L)))
                .willReturn(List.of(
                        review(firstAssignment, "80.00"),
                        review(secondAssignment, "90.00")
                ));

        ReviewRoundFinalizeRes response = service.finalizeRound(
                10L,
                "contest-public-id",
                30L,
                null
        );

        assertThat(response.status()).isEqualTo(ReviewRoundStatus.FINALIZED);
        assertThat(response.finalizedAt()).isEqualTo(NOW);
        assertThat(response.entries())
                .extracting(
                        entry -> entry.submissionPublicId(),
                        entry -> entry.finalScore(),
                        entry -> entry.rankNo(),
                        entry -> entry.status(),
                        entry -> entry.decisionType())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "submission-b",
                                new BigDecimal("90.00"),
                                1,
                                ReviewRoundEntryStatus.SELECTED,
                                ReviewDecisionType.RULE),
                        org.assertj.core.groups.Tuple.tuple(
                                "submission-a",
                                new BigDecimal("80.00"),
                                2,
                                ReviewRoundEntryStatus.NOT_SELECTED,
                                ReviewDecisionType.RULE)
                );
        verify(reviewRoundEntryRepository).flush();
        verify(reviewRoundRepository).flush();
    }

    @Test
    @DisplayName("여러 심사위원 점수의 평균을 소수 둘째 자리에서 HALF_UP으로 반올림한다")
    void finalizeRound_averagesMultipleJudgesWithHalfUpRounding() {
        ReviewRound round = round(
                ReviewRoundDecisionRule.TOP_N,
                1,
                null
        );
        ReviewRoundEntry entry =
                entry(100L, round, "submission-a", "A팀");
        ReviewAssignment firstAssignment =
                completedAssignment(1000L, entry);
        ReviewAssignment secondAssignment =
                completedAssignment(1001L, entry);
        ReviewAssignment thirdAssignment =
                completedAssignment(1002L, entry);
        stubCommon(round, List.of(entry));
        given(reviewAssignmentRepository
                .findAllForShareByReviewRoundEntryIdInOrderByEntryIdAscIdAsc(
                        List.of(100L)))
                .willReturn(List.of(
                        firstAssignment,
                        secondAssignment,
                        thirdAssignment
                ));
        given(reviewRepository.findAllByAssignmentIdIn(
                List.of(1000L, 1001L, 1002L)))
                .willReturn(List.of(
                        review(firstAssignment, "80.00"),
                        review(secondAssignment, "80.01"),
                        review(thirdAssignment, "80.01")
                ));

        ReviewRoundFinalizeRes response = service.finalizeRound(
                10L,
                "contest-public-id",
                30L,
                null
        );

        assertThat(response.entries()).singleElement().satisfies(result -> {
            assertThat(result.finalScore())
                    .isEqualByComparingTo("80.01");
            assertThat(result.rankNo()).isEqualTo(1);
            assertThat(result.status())
                    .isEqualTo(ReviewRoundEntryStatus.SELECTED);
        });
    }

    @Test
    @DisplayName("평균 점수가 같으면 엔트리 ID가 작은 대상을 먼저 순위와 TOP_N에 반영한다")
    void finalizeRound_breaksScoreTieByLowerEntryId() {
        ReviewRound round = round(
                ReviewRoundDecisionRule.TOP_N,
                1,
                null
        );
        ReviewRoundEntry lowerIdEntry =
                entry(100L, round, "submission-a", "A팀");
        ReviewRoundEntry higherIdEntry =
                entry(101L, round, "submission-b", "B팀");
        ReviewAssignment lowerIdAssignment =
                completedAssignment(1000L, lowerIdEntry);
        ReviewAssignment higherIdAssignment =
                completedAssignment(1001L, higherIdEntry);
        stubCommon(round, List.of(lowerIdEntry, higherIdEntry));
        given(reviewAssignmentRepository
                .findAllForShareByReviewRoundEntryIdInOrderByEntryIdAscIdAsc(
                        List.of(100L, 101L)))
                .willReturn(List.of(
                        lowerIdAssignment,
                        higherIdAssignment
                ));
        given(reviewRepository.findAllByAssignmentIdIn(
                List.of(1000L, 1001L)))
                .willReturn(List.of(
                        review(lowerIdAssignment, "85.00"),
                        review(higherIdAssignment, "85.00")
                ));

        ReviewRoundFinalizeRes response = service.finalizeRound(
                10L,
                "contest-public-id",
                30L,
                null
        );

        assertThat(response.entries())
                .extracting(
                        result -> result.id(),
                        result -> result.rankNo(),
                        result -> result.status())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                100L,
                                1,
                                ReviewRoundEntryStatus.SELECTED),
                        org.assertj.core.groups.Tuple.tuple(
                                101L,
                                2,
                                ReviewRoundEntryStatus.NOT_SELECTED)
                );
    }

    @Test
    @DisplayName("MIN_SCORE는 기준 점수와 같은 대상을 선정하고 미달 대상은 탈락시킨다")
    void finalizeRound_appliesMinScoreBoundaryInclusively() {
        ReviewRound round = round(
                ReviewRoundDecisionRule.MIN_SCORE,
                null,
                new BigDecimal("70.00")
        );
        ReviewRoundEntry boundaryEntry =
                entry(100L, round, "submission-a", "A팀");
        ReviewRoundEntry belowEntry =
                entry(101L, round, "submission-b", "B팀");
        ReviewAssignment boundaryAssignment =
                completedAssignment(1000L, boundaryEntry);
        ReviewAssignment belowAssignment =
                completedAssignment(1001L, belowEntry);
        stubCommon(round, List.of(boundaryEntry, belowEntry));
        given(reviewAssignmentRepository
                .findAllForShareByReviewRoundEntryIdInOrderByEntryIdAscIdAsc(
                        List.of(100L, 101L)))
                .willReturn(List.of(
                        boundaryAssignment,
                        belowAssignment
                ));
        given(reviewRepository.findAllByAssignmentIdIn(
                List.of(1000L, 1001L)))
                .willReturn(List.of(
                        review(boundaryAssignment, "70.00"),
                        review(belowAssignment, "69.99")
                ));

        ReviewRoundFinalizeRes response = service.finalizeRound(
                10L,
                "contest-public-id",
                30L,
                null
        );

        assertThat(response.entries())
                .extracting(
                        result -> result.submissionPublicId(),
                        result -> result.finalScore(),
                        result -> result.status())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "submission-a",
                                new BigDecimal("70.00"),
                                ReviewRoundEntryStatus.SELECTED),
                        org.assertj.core.groups.Tuple.tuple(
                                "submission-b",
                                new BigDecimal("69.99"),
                                ReviewRoundEntryStatus.NOT_SELECTED)
                );
    }

    @Test
    @DisplayName("수동 판정은 모든 대상의 결정과 사유를 원자적으로 저장한다")
    void finalizeRound_appliesManualDecisions() {
        ReviewRound round = round(
                ReviewRoundDecisionRule.MANUAL,
                null,
                null
        );
        ReviewRoundEntry entry =
                entry(100L, round, "submission-a", "A팀");
        ReviewAssignment assignment = completedAssignment(1000L, entry);
        stubCommon(round, List.of(entry));
        given(reviewAssignmentRepository
                .findAllForShareByReviewRoundEntryIdInOrderByEntryIdAscIdAsc(
                        List.of(100L)))
                .willReturn(List.of(assignment));
        given(reviewRepository.findAllByAssignmentIdIn(List.of(1000L)))
                .willReturn(List.of(review(assignment, "88.50")));

        ReviewRoundFinalizeRes response = service.finalizeRound(
                10L,
                "contest-public-id",
                30L,
                new ReviewRoundFinalizeReq(List.of(
                        new ReviewManualDecisionReq(
                                100L,
                                ReviewRoundEntryStatus.SELECTED,
                                "사업화 가능성이 높음")
                ))
        );

        assertThat(response.entries()).singleElement().satisfies(result -> {
            assertThat(result.status())
                    .isEqualTo(ReviewRoundEntryStatus.SELECTED);
            assertThat(result.finalScore())
                    .isEqualByComparingTo("88.50");
            assertThat(result.decisionType())
                    .isEqualTo(ReviewDecisionType.MANUAL);
            assertThat(result.decisionReason())
                    .isEqualTo("사업화 가능성이 높음");
        });
        assertThat(entry.getDecidedByUser()).isSameAs(admin);
    }

    @Test
    @DisplayName("수동 대상·수동 판정 라운드는 심사 배정과 점수 없이 확정한다")
    void finalizeRound_supportsManualDecisionWithoutReviews() {
        ReviewRound round = manualWithoutReviewRound();
        ReviewRoundEntry entry =
                entry(100L, round, "submission-a", "A팀");
        stubCommon(round, List.of(entry));
        given(reviewAssignmentRepository
                .findAllForShareByReviewRoundEntryIdInOrderByEntryIdAscIdAsc(
                        List.of(100L)))
                .willReturn(List.of());

        ReviewRoundFinalizeRes response = service.finalizeRound(
                10L,
                "contest-public-id",
                30L,
                new ReviewRoundFinalizeReq(List.of(
                        new ReviewManualDecisionReq(
                                100L,
                                ReviewRoundEntryStatus.SELECTED,
                                "위원회 수동 선정",
                                1)
                ))
        );

        assertThat(response.status())
                .isEqualTo(ReviewRoundStatus.FINALIZED);
        assertThat(response.entries()).singleElement().satisfies(result -> {
            assertThat(result.status())
                    .isEqualTo(ReviewRoundEntryStatus.SELECTED);
            assertThat(result.finalScore()).isNull();
            assertThat(result.rankNo()).isEqualTo(1);
            assertThat(result.decisionType())
                    .isEqualTo(ReviewDecisionType.MANUAL);
            assertThat(result.decidedByUserId()).isEqualTo(10L);
            assertThat(result.decisionReason())
                    .isEqualTo("위원회 수동 선정");
        });
        verifyNoInteractions(reviewRepository);
        assertThat(entry.isFinalized()).isTrue();
    }

    @Test
    @DisplayName("심사 없는 수동 판정은 누락·중복·빈 순위가 있는 요청을 거부한다")
    void finalizeRound_rejectsInvalidManualRanksWithoutReviews() {
        ReviewRound round = manualWithoutReviewRound();
        ReviewRoundEntry first =
                entry(100L, round, "submission-a", "A팀");
        ReviewRoundEntry second =
                entry(101L, round, "submission-b", "B팀");
        stubCommon(round, List.of(first, second));

        List<ReviewRoundFinalizeReq> invalidRequests = List.of(
                new ReviewRoundFinalizeReq(List.of(
                        new ReviewManualDecisionReq(
                                100L,
                                ReviewRoundEntryStatus.SELECTED,
                                "첫 번째",
                                null),
                        new ReviewManualDecisionReq(
                                101L,
                                ReviewRoundEntryStatus.NOT_SELECTED,
                                "두 번째",
                                2)
                )),
                new ReviewRoundFinalizeReq(List.of(
                        new ReviewManualDecisionReq(
                                100L,
                                ReviewRoundEntryStatus.SELECTED,
                                "첫 번째",
                                1),
                        new ReviewManualDecisionReq(
                                101L,
                                ReviewRoundEntryStatus.NOT_SELECTED,
                                "두 번째",
                                1)
                )),
                new ReviewRoundFinalizeReq(List.of(
                        new ReviewManualDecisionReq(
                                100L,
                                ReviewRoundEntryStatus.SELECTED,
                                "첫 번째",
                                1),
                        new ReviewManualDecisionReq(
                                101L,
                                ReviewRoundEntryStatus.NOT_SELECTED,
                                "두 번째",
                                3)
                ))
        );

        invalidRequests.forEach(request ->
                assertThatThrownBy(() -> service.finalizeRound(
                        10L,
                        "contest-public-id",
                        30L,
                        request
                ))
                        .isInstanceOf(CustomException.class)
                        .extracting(error ->
                                ((CustomException) error)
                                        .getBaseResponseCode())
                        .isEqualTo(ReviewErrorResponseCode
                                .REVIEW_MANUAL_DECISION_INVALID));

        verifyNoInteractions(reviewAssignmentRepository);
        assertThat(round.getStatus()).isEqualTo(ReviewRoundStatus.OPEN);
    }

    @Test
    @DisplayName("점수 심사를 거친 수동 판정에는 관리자가 별도 순위를 전달할 수 없다")
    void finalizeRound_rejectsExplicitRankForScoredManualDecision() {
        ReviewRound round = round(
                ReviewRoundDecisionRule.MANUAL,
                null,
                null);
        ReviewRoundEntry entry =
                entry(100L, round, "submission-a", "A팀");
        stubCommon(round, List.of(entry));

        assertThatThrownBy(() -> service.finalizeRound(
                10L,
                "contest-public-id",
                30L,
                new ReviewRoundFinalizeReq(List.of(
                        new ReviewManualDecisionReq(
                                100L,
                                ReviewRoundEntryStatus.SELECTED,
                                "수동 선정",
                                1)
                ))
        ))
                .isInstanceOf(CustomException.class)
                .extracting(error -> ((CustomException) error)
                        .getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode
                        .REVIEW_MANUAL_DECISION_INVALID);

        verifyNoInteractions(reviewAssignmentRepository);
    }

    @Test
    @DisplayName("배정 상태만 완료이고 실제 리뷰가 없으면 라운드를 확정하지 않는다")
    void finalizeRound_rejectsCompletedAssignmentWithoutReview() {
        ReviewRound round = round(
                ReviewRoundDecisionRule.TOP_N,
                1,
                null);
        ReviewRoundEntry entry =
                entry(100L, round, "submission-a", "A팀");
        ReviewAssignment assignment = completedAssignment(1000L, entry);
        stubCommon(round, List.of(entry));
        given(reviewAssignmentRepository
                .findAllForShareByReviewRoundEntryIdInOrderByEntryIdAscIdAsc(
                        List.of(100L)))
                .willReturn(List.of(assignment));
        given(reviewRepository.findAllByAssignmentIdIn(List.of(1000L)))
                .willReturn(List.of());

        assertThatThrownBy(() -> service.finalizeRound(
                10L,
                "contest-public-id",
                30L,
                null
        ))
                .isInstanceOf(CustomException.class)
                .extracting(error -> ((CustomException) error)
                        .getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode
                        .REVIEW_ROUND_ASSIGNMENTS_INCOMPLETE);

        assertThat(round.getStatus()).isEqualTo(ReviewRoundStatus.OPEN);
    }

    @Test
    @DisplayName("활성 배정 중 미제출 심사가 있으면 라운드를 확정하지 않는다")
    void finalizeRound_rejectsIncompleteAssignments() {
        ReviewRound round = round(
                ReviewRoundDecisionRule.MIN_SCORE,
                null,
                new BigDecimal("70.00")
        );
        ReviewRoundEntry entry =
                entry(100L, round, "submission-a", "A팀");
        ReviewAssignment pending = ReviewAssignment.builder()
                .id(1000L)
                .reviewRoundEntry(entry)
                .status(ReviewAssignmentStatus.ASSIGNED)
                .assignedAt(NOW.minusDays(1))
                .build();
        stubCommon(round, List.of(entry));
        given(reviewAssignmentRepository
                .findAllForShareByReviewRoundEntryIdInOrderByEntryIdAscIdAsc(
                        List.of(100L)))
                .willReturn(List.of(pending));

        assertThatThrownBy(() -> service.finalizeRound(
                10L,
                "contest-public-id",
                30L,
                null
        ))
                .isInstanceOf(CustomException.class)
                .extracting(error -> ((CustomException) error)
                        .getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode
                        .REVIEW_ROUND_ASSIGNMENTS_INCOMPLETE);

        verifyNoInteractions(reviewRepository);
        assertThat(round.getStatus()).isEqualTo(ReviewRoundStatus.OPEN);
    }

    @Test
    @DisplayName("수상이 확정된 대회의 남은 라운드는 뒤늦게 확정할 수 없다")
    void finalizeRound_rejectsAwardedContest() {
        contest.changeStatus(ContestStatus.AWARDED);
        given(userRepository.findById(10L))
                .willReturn(Optional.of(admin));
        given(contestRepository
                .findByPublicIdForUpdate("contest-public-id"))
                .willReturn(Optional.of(contest));

        assertThatThrownBy(() -> service.finalizeRound(
                10L,
                "contest-public-id",
                30L,
                null
        ))
                .isInstanceOf(CustomException.class)
                .extracting(error -> ((CustomException) error)
                        .getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode
                        .REVIEW_ROUND_FINALIZATION_NOT_ALLOWED);

        verifyNoInteractions(reviewRoundRepository);
    }

    private void stubCommon(
            ReviewRound round,
            List<ReviewRoundEntry> entries
    ) {
        given(userRepository.findById(10L))
                .willReturn(Optional.of(admin));
        given(contestRepository
                .findByPublicIdForUpdate("contest-public-id"))
                .willReturn(Optional.of(contest));
        given(reviewRoundRepository.findOrganizationIdById(30L))
                .willReturn(Optional.of(1L));
        given(reviewRoundRepository.findByIdForUpdate(30L))
                .willReturn(Optional.of(round));
        given(reviewRoundEntryRepository
                .findAllForUpdateByReviewRoundIdOrderByIdAsc(30L))
                .willReturn(entries);
    }

    private ReviewRound round(
            ReviewRoundDecisionRule rule,
            Integer selectCount,
            BigDecimal minScore
    ) {
        return ReviewRound.builder()
                .id(30L)
                .contest(contest)
                .roundNo(1)
                .name("본선")
                .status(ReviewRoundStatus.OPEN)
                .startsAt(NOW.minusDays(1))
                .endsAt(NOW.plusDays(1))
                .targetType(ReviewRoundTargetType.ALL_SUBMISSIONS)
                .decisionRule(rule)
                .selectCount(selectCount)
                .minScore(minScore)
                .build();
    }

    private ReviewRound manualWithoutReviewRound() {
        return ReviewRound.builder()
                .id(30L)
                .contest(contest)
                .roundNo(1)
                .name("수동 선정")
                .status(ReviewRoundStatus.OPEN)
                .startsAt(NOW.minusDays(1))
                .endsAt(NOW.plusDays(1))
                .targetType(ReviewRoundTargetType.MANUAL)
                .decisionRule(ReviewRoundDecisionRule.MANUAL)
                .build();
    }

    private ReviewRoundEntry entry(
            Long id,
            ReviewRound round,
            String submissionPublicId,
            String teamName
    ) {
        Team team = Team.builder()
                .id(id + 1000)
                .name(teamName)
                .build();
        Submission submission = Submission.builder()
                .id(id + 2000)
                .publicId(submissionPublicId)
                .team(team)
                .title(teamName + " 작품")
                .build();
        return ReviewRoundEntry.builder()
                .id(id)
                .reviewRound(round)
                .submission(submission)
                .status(ReviewRoundEntryStatus.IN_REVIEW)
                .build();
    }

    private ReviewAssignment completedAssignment(
            Long id,
            ReviewRoundEntry entry
    ) {
        return ReviewAssignment.builder()
                .id(id)
                .reviewRoundEntry(entry)
                .status(ReviewAssignmentStatus.COMPLETED)
                .assignedAt(NOW.minusDays(1))
                .completedAt(NOW.minusMinutes(1))
                .build();
    }

    private Review review(
            ReviewAssignment assignment,
            String totalScore
    ) {
        return Review.builder()
                .assignment(assignment)
                .totalScore(new BigDecimal(totalScore))
                .submittedAt(NOW.minusMinutes(1))
                .build();
    }
}
