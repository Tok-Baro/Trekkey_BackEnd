package com.api.trekkey.domain.review.publicapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStatus;
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
import com.api.trekkey.domain.review.repository.ReviewAssignmentRepository;
import com.api.trekkey.domain.review.repository.ReviewAssignmentRepository.ReviewSubmissionScope;
import com.api.trekkey.domain.review.repository.ReviewCriterionRepository;
import com.api.trekkey.domain.review.repository.ReviewRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundEntryRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundRepository;
import com.api.trekkey.domain.review.repository.ReviewScoreItemRepository;
import com.api.trekkey.domain.review.support.ReviewLinkAuthenticator;
import com.api.trekkey.domain.review.publicapi.web.dto.request.ReviewScoreReq;
import com.api.trekkey.domain.review.publicapi.web.dto.request.ReviewSubmitReq;
import com.api.trekkey.domain.review.publicapi.web.dto.response.ReviewSubmitRes;
import com.api.trekkey.global.exception.CustomException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReviewSubmissionServiceImplTest {

    private static final String RAW_TOKEN = "a".repeat(43);
    private static final Long CONTEST_ID = 100L;
    private static final Long ROUND_ID = 200L;
    private static final Long JUDGE_ID = 300L;
    private static final Long ENTRY_ID = 400L;
    private static final Long ASSIGNMENT_ID = 500L;
    private static final Long REVIEW_ID = 600L;
    private static final Long CREATIVITY_ID = 701L;
    private static final Long COMPLETENESS_ID = 702L;
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 7, 27, 12, 0);

    @Mock
    private ReviewLinkAuthenticator reviewLinkAuthenticator;

    @Mock
    private ReviewRoundRepository reviewRoundRepository;

    @Mock
    private ReviewCriterionRepository reviewCriterionRepository;

    @Mock
    private ReviewRoundEntryRepository reviewRoundEntryRepository;

    @Mock
    private ReviewAssignmentRepository reviewAssignmentRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewScoreItemRepository reviewScoreItemRepository;

    private ReviewSubmissionServiceImpl service;
    private Contest contest;
    private ContestJudge judge;
    private ReviewRound round;
    private ReviewCriterion creativity;
    private ReviewCriterion completeness;
    private List<ReviewCriterion> criteria;
    private ReviewRoundEntry entry;
    private ReviewAssignment assignment;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                NOW.atZone(ZONE_ID).toInstant(),
                ZONE_ID
        );
        service = new ReviewSubmissionServiceImpl(
                reviewLinkAuthenticator,
                reviewRoundRepository,
                reviewCriterionRepository,
                reviewRoundEntryRepository,
                reviewAssignmentRepository,
                reviewRepository,
                reviewScoreItemRepository,
                clock
        );

        contest = contest(CONTEST_ID, ContestStatus.PREPARING);
        judge = judge(JUDGE_ID, contest);
        round = round(
                ROUND_ID,
                contest,
                ReviewRoundStatus.OPEN,
                NOW.minusHours(1),
                NOW.plusHours(1)
        );
        creativity = criterion(
                CREATIVITY_ID,
                round,
                "creativity",
                40,
                1,
                true
        );
        completeness = criterion(
                COMPLETENESS_ID,
                round,
                "completeness",
                60,
                2,
                true
        );
        criteria = List.of(creativity, completeness);
        entry = entry(
                ENTRY_ID,
                round,
                ReviewRoundEntryStatus.IN_REVIEW
        );
        assignment = assignment(
                ASSIGNMENT_ID,
                judge,
                entry,
                ReviewAssignmentStatus.ASSIGNED,
                NOW.plusHours(1)
        );
    }

    @Test
    @DisplayName("잠금 순서대로 채점을 저장하고 서버 합산 후 배정을 완료한다")
    @SuppressWarnings("unchecked")
    void submitReview_savesServerCalculatedScoreAndCompletesAssignmentInLockOrder() {
        stubLockedContext(null);
        stubSuccessfulSaves();
        ReviewSubmitReq request = request(
                "좋은 작품입니다.",
                score(CREATIVITY_ID, "35.25"),
                score(COMPLETENESS_ID, "54.50")
        );

        ReviewSubmitRes response =
                service.submitReview(ASSIGNMENT_ID, request);

        assertThat(response.reviewId()).isEqualTo(REVIEW_ID);
        assertThat(response.assignmentId()).isEqualTo(ASSIGNMENT_ID);
        assertThat(response.totalScore())
                .isEqualByComparingTo("89.75");
        assertThat(response.comment()).isEqualTo("좋은 작품입니다.");
        assertThat(response.submittedAt()).isEqualTo(NOW);
        assertThat(response.scores())
                .extracting(item -> item.criterionId())
                .containsExactly(CREATIVITY_ID, COMPLETENESS_ID);

        ArgumentCaptor<Review> reviewCaptor =
                ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository)
                .saveAndFlush(reviewCaptor.capture());
        assertThat(reviewCaptor.getValue().getTotalScore())
                .isEqualByComparingTo("89.75");
        assertThat(reviewCaptor.getValue().getSubmittedAt())
                .isEqualTo(NOW);

        ArgumentCaptor<List<ReviewScoreItem>> itemsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(reviewScoreItemRepository)
                .saveAllAndFlush(itemsCaptor.capture());
        assertThat(itemsCaptor.getValue()).hasSize(2);
        assertThat(itemsCaptor.getValue())
                .extracting(item -> item.getReviewCriterion().getId())
                .containsExactly(CREATIVITY_ID, COMPLETENESS_ID);
        assertThat(itemsCaptor.getValue())
                .extracting(ReviewScoreItem::getScore)
                .usingComparatorForType(
                        BigDecimal::compareTo,
                        BigDecimal.class
                )
                .containsExactly(
                        new BigDecimal("35.25"),
                        new BigDecimal("54.50")
                );
        assertThat(assignment.getStatus())
                .isEqualTo(ReviewAssignmentStatus.COMPLETED);
        assertThat(assignment.getCompletedAt()).isEqualTo(NOW);

        InOrder lockOrder = inOrder(
                reviewLinkAuthenticator,
                reviewAssignmentRepository,
                reviewRoundRepository,
                reviewCriterionRepository,
                reviewRoundEntryRepository,
                reviewRepository,
                reviewScoreItemRepository
        );
        lockOrder.verify(reviewLinkAuthenticator)
                .authenticate(RAW_TOKEN, NOW);
        lockOrder.verify(reviewAssignmentRepository)
                .findSubmissionScopeByIdAndJudgeId(
                        ASSIGNMENT_ID,
                        JUDGE_ID
                );
        lockOrder.verify(reviewRoundRepository)
                .findByIdForShare(ROUND_ID);
        lockOrder.verify(reviewCriterionRepository)
                .findAllForShareByReviewRoundIdOrderBySortOrderAsc(
                        ROUND_ID);
        lockOrder.verify(reviewRoundEntryRepository)
                .findByIdAndReviewRoundIdForShare(
                        ENTRY_ID,
                        ROUND_ID
                );
        lockOrder.verify(reviewAssignmentRepository)
                .findByIdAndJudgeIdAndEntryIdForUpdate(
                        ASSIGNMENT_ID,
                        JUDGE_ID,
                        ENTRY_ID
                );
        lockOrder.verify(reviewRepository)
                .findByAssignmentIdForShare(ASSIGNMENT_ID);
        lockOrder.verify(reviewRepository)
                .saveAndFlush(any(Review.class));
        lockOrder.verify(reviewScoreItemRepository)
                .saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("점수 0과 기준의 최대 점수는 유효한 경계값으로 저장한다")
    void submitReview_allowsZeroAndMaximumScore() {
        stubLockedContext(null);
        stubSuccessfulSaves();

        ReviewSubmitRes response = service.submitReview(
                ASSIGNMENT_ID,
                request(
                        null,
                        score(CREATIVITY_ID, "0"),
                        score(COMPLETENESS_ID, "60")
                )
        );

        assertThat(response.totalScore())
                .isEqualByComparingTo("60.00");
        assertThat(response.scores())
                .extracting(item -> item.score())
                .usingComparatorForType(
                        BigDecimal::compareTo,
                        BigDecimal.class
                )
                .containsExactly(
                        new BigDecimal("0.00"),
                        new BigDecimal("60.00")
                );
    }

    @Test
    @DisplayName("완료된 배정의 동일 채점은 점수 순서와 BigDecimal scale이 달라도 기존 결과를 반환한다")
    void submitReview_returnsExistingReviewForSemanticallyIdenticalRetry() {
        LocalDateTime originallySubmittedAt = NOW.minusHours(2);
        completeAssignmentAt(originallySubmittedAt);
        ReflectionTestUtils.setField(
                contest,
                "status",
                ContestStatus.AWARDED
        );
        ReflectionTestUtils.setField(
                round,
                "status",
                ReviewRoundStatus.FINALIZED
        );
        ReflectionTestUtils.setField(
                entry,
                "status",
                ReviewRoundEntryStatus.SELECTED
        );
        ReflectionTestUtils.setField(
                assignment,
                "dueAt",
                NOW.minusHours(1)
        );
        Review existingReview = review(
                REVIEW_ID,
                assignment,
                "30.00",
                "동일 의견",
                originallySubmittedAt
        );
        List<ReviewScoreItem> existingItems = List.of(
                reviewScoreItem(
                        801L,
                        existingReview,
                        creativity,
                        "10.00"
                ),
                reviewScoreItem(
                        802L,
                        existingReview,
                        completeness,
                        "20.0"
                )
        );
        stubLockedContext(existingReview);
        given(reviewScoreItemRepository
                .findAllForShareByReviewIdOrderByCriterionIdAsc(
                        REVIEW_ID))
                .willReturn(existingItems);

        ReviewSubmitRes response = service.submitReview(
                ASSIGNMENT_ID,
                request(
                        "동일 의견",
                        score(COMPLETENESS_ID, "20.00"),
                        score(CREATIVITY_ID, "10.0")
                )
        );

        assertThat(response.reviewId()).isEqualTo(REVIEW_ID);
        assertThat(response.submittedAt())
                .isEqualTo(originallySubmittedAt);
        assertThat(response.totalScore())
                .isEqualByComparingTo("30");
        assertThat(response.scores())
                .extracting(item -> item.criterionId())
                .containsExactly(CREATIVITY_ID, COMPLETENESS_ID);
        verify(reviewRepository, never())
                .saveAndFlush(any(Review.class));
        verify(reviewScoreItemRepository, never())
                .saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("완료된 배정에 Review가 없으면 저장 상태 불일치로 거부한다")
    void submitReview_rejectsCompletedAssignmentWithoutReview() {
        completeAssignmentAt(NOW.minusHours(1));
        stubLockedContext(null);

        assertReviewError(
                ReviewErrorResponseCode
                        .REVIEW_SUBMISSION_STATE_INVALID,
                () -> service.submitReview(
                        ASSIGNMENT_ID,
                        validRequest()
                )
        );
    }

    @Test
    @DisplayName("진행 중인 배정에 Review가 이미 있으면 저장 상태 불일치로 거부한다")
    void submitReview_rejectsExistingReviewForAssignedAssignment() {
        Review existingReview = review(
                REVIEW_ID,
                assignment,
                "30.00",
                "기존 의견",
                NOW.minusHours(1)
        );
        stubLockedContext(existingReview);

        assertReviewError(
                ReviewErrorResponseCode
                        .REVIEW_SUBMISSION_STATE_INVALID,
                () -> service.submitReview(
                        ASSIGNMENT_ID,
                        validRequest()
                )
        );
        verify(reviewScoreItemRepository, never())
                .findAllForShareByReviewIdOrderByCriterionIdAsc(
                        REVIEW_ID);
    }

    @Test
    @DisplayName("완료된 채점의 점수가 바뀐 재요청은 409 오류로 거부한다")
    void submitReview_rejectsRetryWithChangedScore() {
        Review existingReview = completedReview(
                "동일 의견",
                "10.00",
                "20.00"
        );
        stubLockedContext(existingReview);
        stubExistingScoreItems(
                existingReview,
                "10.00",
                "20.00"
        );

        assertReviewError(
                ReviewErrorResponseCode.REVIEW_ALREADY_SUBMITTED,
                () -> service.submitReview(
                        ASSIGNMENT_ID,
                        request(
                                "동일 의견",
                                score(CREATIVITY_ID, "11.00"),
                                score(COMPLETENESS_ID, "20.00")
                        )
                )
        );
        assertThat(
                ReviewErrorResponseCode
                        .REVIEW_ALREADY_SUBMITTED
                        .getHttpStatus()
        ).isEqualTo(409);
        verify(reviewRepository, never())
                .saveAndFlush(any(Review.class));
    }

    @Test
    @DisplayName("완료된 채점의 의견이 바뀐 재요청은 409 오류로 거부한다")
    void submitReview_rejectsRetryWithChangedComment() {
        Review existingReview = completedReview(
                "원래 의견",
                "10.00",
                "20.00"
        );
        stubLockedContext(existingReview);
        stubExistingScoreItems(
                existingReview,
                "10.00",
                "20.00"
        );

        assertReviewError(
                ReviewErrorResponseCode.REVIEW_ALREADY_SUBMITTED,
                () -> service.submitReview(
                        ASSIGNMENT_ID,
                        request(
                                "변경된 의견",
                                score(CREATIVITY_ID, "10.00"),
                                score(COMPLETENESS_ID, "20.00")
                        )
                )
        );
        verify(reviewScoreItemRepository, never())
                .saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("다른 심사위원 토큰으로 배정 ID를 조회하면 존재 여부를 숨기고 404를 반환한다")
    void submitReview_hidesAssignmentFromDifferentJudge() {
        ContestJudge otherJudge = judge(999L, contest);
        given(reviewLinkAuthenticator.authenticate(RAW_TOKEN, NOW))
                .willReturn(otherJudge);
        given(reviewAssignmentRepository
                .findSubmissionScopeByIdAndJudgeId(
                        ASSIGNMENT_ID,
                        999L
                )).willReturn(Optional.empty());

        assertReviewError(
                ReviewErrorResponseCode.REVIEW_ASSIGNMENT_NOT_FOUND,
                () -> service.submitReview(
                        ASSIGNMENT_ID,
                        validRequest()
                )
        );
        assertThat(
                ReviewErrorResponseCode
                        .REVIEW_ASSIGNMENT_NOT_FOUND
                        .getHttpStatus()
        ).isEqualTo(404);
        verifyNoInteractions(
                reviewRoundRepository,
                reviewCriterionRepository,
                reviewRoundEntryRepository,
                reviewRepository,
                reviewScoreItemRepository
        );
    }

    @Test
    @DisplayName("유효하지 않은 링크는 배정 정보를 조회하기 전에 거부한다")
    void submitReview_rejectsInvalidLinkBeforeAssignmentLookup() {
        given(reviewLinkAuthenticator.authenticate(RAW_TOKEN, NOW))
                .willThrow(new CustomException(
                        ReviewErrorResponseCode.REVIEW_LINK_INVALID));

        assertReviewError(
                ReviewErrorResponseCode.REVIEW_LINK_INVALID,
                () -> service.submitReview(
                        ASSIGNMENT_ID,
                        validRequest()
                )
        );
        verifyNoInteractions(
                reviewRoundRepository,
                reviewCriterionRepository,
                reviewRoundEntryRepository,
                reviewAssignmentRepository,
                reviewRepository,
                reviewScoreItemRepository
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("notOpenRoundCases")
    @DisplayName("라운드 상태와 실제 심사 시간 경계 밖에서는 제출할 수 없다")
    void submitReview_rejectsRoundOutsideOpenWindow(
            String ignoredName,
            ReviewRoundStatus status,
            LocalDateTime startsAt,
            LocalDateTime endsAt
    ) {
        ReflectionTestUtils.setField(round, "status", status);
        ReflectionTestUtils.setField(round, "startsAt", startsAt);
        ReflectionTestUtils.setField(round, "endsAt", endsAt);
        stubLockedContext(null);

        assertReviewError(
                ReviewErrorResponseCode.REVIEW_SUBMISSION_NOT_OPEN,
                () -> service.submitReview(
                        ASSIGNMENT_ID,
                        validRequest()
                )
        );
        assertThat(assignment.getStatus())
                .isEqualTo(ReviewAssignmentStatus.ASSIGNED);
    }

    @Test
    @DisplayName("대회 상태와 무관하게 OPEN 시간 안의 라운드는 채점을 제출할 수 있다")
    void submitReview_allowsOpenRoundRegardlessOfContestStatus() {
        ReflectionTestUtils.setField(
                contest,
                "status",
                ContestStatus.AWARDED
        );
        stubLockedContext(null);
        stubSuccessfulSaves();

        ReviewSubmitRes response =
                service.submitReview(ASSIGNMENT_ID, validRequest());

        assertThat(response.reviewId()).isEqualTo(REVIEW_ID);
        assertThat(assignment.getStatus())
                .isEqualTo(ReviewAssignmentStatus.COMPLETED);
    }

    @Test
    @DisplayName("심사 대상이 IN_REVIEW 상태가 아니면 채점을 제출할 수 없다")
    void submitReview_rejectsEntryThatIsNotInReview() {
        ReflectionTestUtils.setField(
                entry,
                "status",
                ReviewRoundEntryStatus.ELIGIBLE
        );
        stubLockedContext(null);

        assertReviewError(
                ReviewErrorResponseCode.REVIEW_SUBMISSION_NOT_ALLOWED,
                () -> service.submitReview(
                        ASSIGNMENT_ID,
                        validRequest()
                )
        );
    }

    @Test
    @DisplayName("취소된 배정에는 채점을 제출할 수 없다")
    void submitReview_rejectsCanceledAssignment() {
        assertThat(assignment.cancel()).isTrue();
        stubLockedContext(null);

        assertReviewError(
                ReviewErrorResponseCode.REVIEW_SUBMISSION_NOT_ALLOWED,
                () -> service.submitReview(
                        ASSIGNMENT_ID,
                        validRequest()
                )
        );
    }

    @Test
    @DisplayName("현재 시각이 배정 마감 시각과 같으면 제출 기한이 지난 것으로 처리한다")
    void submitReview_rejectsAtAssignmentDueAtBoundary() {
        ReflectionTestUtils.setField(assignment, "dueAt", NOW);
        stubLockedContext(null);

        assertReviewError(
                ReviewErrorResponseCode
                        .REVIEW_SUBMISSION_DEADLINE_EXPIRED,
                () -> service.submitReview(
                        ASSIGNMENT_ID,
                        validRequest()
                )
        );
    }

    @Test
    @DisplayName("활성 평가 기준 하나가 누락되면 제출을 거부한다")
    void submitReview_rejectsMissingCriterion() {
        stubLockedContext(null);

        assertReviewError(
                ReviewErrorResponseCode
                        .REVIEW_SCORE_CRITERIA_MISMATCH,
                () -> service.submitReview(
                        ASSIGNMENT_ID,
                        request(
                                null,
                                score(CREATIVITY_ID, "10")
                        )
                )
        );
    }

    @Test
    @DisplayName("현재 라운드에 없는 평가 기준이 추가되면 제출을 거부한다")
    void submitReview_rejectsExtraCriterion() {
        stubLockedContext(null);

        assertReviewError(
                ReviewErrorResponseCode
                        .REVIEW_SCORE_CRITERIA_MISMATCH,
                () -> service.submitReview(
                        ASSIGNMENT_ID,
                        request(
                                null,
                                score(CREATIVITY_ID, "10"),
                                score(COMPLETENESS_ID, "20"),
                                score(999L, "1")
                        )
                )
        );
    }

    @Test
    @DisplayName("같은 평가 기준 ID가 중복되면 제출을 거부한다")
    void submitReview_rejectsDuplicatedCriterion() {
        stubLockedContext(null);

        assertReviewError(
                ReviewErrorResponseCode
                        .REVIEW_SCORE_CRITERIA_MISMATCH,
                () -> service.submitReview(
                        ASSIGNMENT_ID,
                        request(
                                null,
                                score(CREATIVITY_ID, "10"),
                                score(CREATIVITY_ID, "20"),
                                score(COMPLETENESS_ID, "30")
                        )
                )
        );
    }

    @Test
    @DisplayName("비활성 평가 기준에 점수를 포함하면 제출을 거부한다")
    void submitReview_rejectsInactiveCriterion() {
        completeness.deactivate();
        stubLockedContext(null);

        assertReviewError(
                ReviewErrorResponseCode
                        .REVIEW_SCORE_CRITERIA_MISMATCH,
                () -> service.submitReview(
                        ASSIGNMENT_ID,
                        validRequest()
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidScoreCases")
    @DisplayName("점수가 기준별 허용 범위와 소수 둘째 자리 제한을 벗어나면 거부한다")
    void submitReview_rejectsInvalidCriterionScore(
            String ignoredName,
            String invalidScore
    ) {
        stubLockedContext(null);

        assertReviewError(
                ReviewErrorResponseCode.REVIEW_SCORE_OUT_OF_RANGE,
                () -> service.submitReview(
                        ASSIGNMENT_ID,
                        request(
                                null,
                                score(CREATIVITY_ID, invalidScore),
                                score(COMPLETENESS_ID, "20")
                        )
                )
        );
    }

    @Test
    @DisplayName("서버 합산 총점이 DECIMAL(12,2) 범위를 넘으면 제출을 거부한다")
    void submitReview_rejectsTotalScoreOverflow() {
        List<ReviewCriterion> largeCriteria = new ArrayList<>();
        List<ReviewScoreReq> largeScores = new ArrayList<>();
        for (long index = 1; index <= 5; index++) {
            long criterionId = 900L + index;
            largeCriteria.add(criterion(
                    criterionId,
                    round,
                    "large-" + index,
                    Integer.MAX_VALUE,
                    (int) index,
                    true
            ));
            largeScores.add(score(
                    criterionId,
                    String.valueOf(Integer.MAX_VALUE)
            ));
        }
        criteria = largeCriteria;
        stubLockedContext(null);

        assertReviewError(
                ReviewErrorResponseCode.REVIEW_SCORE_OUT_OF_RANGE,
                () -> service.submitReview(
                        ASSIGNMENT_ID,
                        new ReviewSubmitReq(
                                RAW_TOKEN,
                                largeScores,
                                null
                        )
                )
        );
    }

    @Test
    @DisplayName("심사 의견이 5000자를 넘으면 제출을 거부한다")
    void submitReview_rejectsCommentLongerThanLimit() {
        stubLockedContext(null);

        assertReviewError(
                ReviewErrorResponseCode.REVIEW_COMMENT_TOO_LONG,
                () -> service.submitReview(
                        ASSIGNMENT_ID,
                        request(
                                "가".repeat(5001),
                                score(CREATIVITY_ID, "10"),
                                score(COMPLETENESS_ID, "20")
                        )
                )
        );
    }

    @Test
    @DisplayName("Review 유니크 제약 충돌은 중복 제출 오류로 변환하고 배정을 완료하지 않는다")
    void submitReview_translatesUniqueConstraintViolation() {
        stubLockedContext(null);
        given(reviewRepository.saveAndFlush(any(Review.class)))
                .willThrow(new DataIntegrityViolationException(
                        "duplicate assignment_id"));

        assertReviewError(
                ReviewErrorResponseCode.REVIEW_SUBMISSION_DUPLICATED,
                () -> service.submitReview(
                        ASSIGNMENT_ID,
                        validRequest()
                )
        );
        assertThat(assignment.getStatus())
                .isEqualTo(ReviewAssignmentStatus.ASSIGNED);
        assertThat(assignment.getCompletedAt()).isNull();
        verify(reviewScoreItemRepository, never())
                .saveAllAndFlush(anyList());
    }

    private void stubLockedContext(Review existingReview) {
        ReviewSubmissionScope scope =
                org.mockito.Mockito.mock(ReviewSubmissionScope.class);
        given(scope.getReviewRoundId()).willReturn(ROUND_ID);
        given(scope.getReviewRoundEntryId()).willReturn(ENTRY_ID);
        given(reviewLinkAuthenticator.authenticate(RAW_TOKEN, NOW))
                .willReturn(judge);
        given(reviewAssignmentRepository
                .findSubmissionScopeByIdAndJudgeId(
                        ASSIGNMENT_ID,
                        JUDGE_ID
                )).willReturn(Optional.of(scope));
        given(reviewRoundRepository.findByIdForShare(ROUND_ID))
                .willReturn(Optional.of(round));
        given(reviewCriterionRepository
                .findAllForShareByReviewRoundIdOrderBySortOrderAsc(
                        ROUND_ID))
                .willReturn(criteria);
        given(reviewRoundEntryRepository
                .findByIdAndReviewRoundIdForShare(
                        ENTRY_ID,
                        ROUND_ID
                )).willReturn(Optional.of(entry));
        given(reviewAssignmentRepository
                .findByIdAndJudgeIdAndEntryIdForUpdate(
                        ASSIGNMENT_ID,
                        JUDGE_ID,
                        ENTRY_ID
                )).willReturn(Optional.of(assignment));
        if (existingReview != null) {
            given(reviewRepository
                    .findByAssignmentIdForShare(ASSIGNMENT_ID))
                    .willReturn(Optional.of(existingReview));
        }
    }

    private void stubSuccessfulSaves() {
        given(reviewRepository.saveAndFlush(any(Review.class)))
                .willAnswer(invocation -> {
                    Review review = invocation.getArgument(0);
                    ReflectionTestUtils.setField(
                            review,
                            "id",
                            REVIEW_ID
                    );
                    return review;
                });
        given(reviewScoreItemRepository.saveAllAndFlush(anyList()))
                .willAnswer(invocation -> {
                    List<ReviewScoreItem> items =
                            invocation.getArgument(0);
                    for (int index = 0; index < items.size(); index++) {
                        ReflectionTestUtils.setField(
                                items.get(index),
                                "id",
                                800L + index
                        );
                    }
                    return items;
                });
    }

    private Review completedReview(
            String comment,
            String creativityScore,
            String completenessScore
    ) {
        completeAssignmentAt(NOW.minusHours(1));
        Review review = review(
                REVIEW_ID,
                assignment,
                new BigDecimal(creativityScore)
                        .add(new BigDecimal(completenessScore))
                        .toPlainString(),
                comment,
                NOW.minusHours(1)
        );
        return review;
    }

    private void stubExistingScoreItems(
            Review review,
            String creativityScore,
            String completenessScore
    ) {
        given(reviewScoreItemRepository
                .findAllForShareByReviewIdOrderByCriterionIdAsc(
                        REVIEW_ID))
                .willReturn(List.of(
                        reviewScoreItem(
                                801L,
                                review,
                                creativity,
                                creativityScore
                        ),
                        reviewScoreItem(
                                802L,
                                review,
                                completeness,
                                completenessScore
                        )
                ));
    }

    private void completeAssignmentAt(LocalDateTime completedAt) {
        assertThat(assignment.complete(completedAt)).isTrue();
    }

    private ReviewSubmitReq validRequest() {
        return request(
                "심사 의견",
                score(CREATIVITY_ID, "30"),
                score(COMPLETENESS_ID, "50")
        );
    }

    private ReviewSubmitReq request(
            String comment,
            ReviewScoreReq... scores
    ) {
        return new ReviewSubmitReq(
                RAW_TOKEN,
                List.of(scores),
                comment
        );
    }

    private ReviewScoreReq score(Long criterionId, String score) {
        return new ReviewScoreReq(
                criterionId,
                new BigDecimal(score)
        );
    }

    private void assertReviewError(
            ReviewErrorResponseCode expected,
            ThrowingCallable callable
    ) {
        assertThatThrownBy(callable)
                .isInstanceOf(CustomException.class)
                .extracting(exception ->
                        ((CustomException) exception)
                                .getBaseResponseCode())
                .isEqualTo(expected);
    }

    private Contest contest(Long id, ContestStatus status) {
        Contest result = Contest.builder()
                .publicId("contest-public-id")
                .title("AI 공모전")
                .status(status)
                .build();
        ReflectionTestUtils.setField(result, "id", id);
        return result;
    }

    private ContestJudge judge(Long id, Contest judgeContest) {
        ContestJudge result = ContestJudge.builder()
                .contest(judgeContest)
                .name("김심사")
                .roleLabel("외부 전문가")
                .build();
        ReflectionTestUtils.setField(result, "id", id);
        return result;
    }

    private ReviewRound round(
            Long id,
            Contest roundContest,
            ReviewRoundStatus status,
            LocalDateTime startsAt,
            LocalDateTime endsAt
    ) {
        ReviewRound result = ReviewRound.builder()
                .contest(roundContest)
                .roundNo(1)
                .name("본선 심사")
                .status(status)
                .startsAt(startsAt)
                .endsAt(endsAt)
                .targetType(ReviewRoundTargetType.MANUAL)
                .decisionRule(ReviewRoundDecisionRule.MANUAL)
                .build();
        ReflectionTestUtils.setField(result, "id", id);
        return result;
    }

    private ReviewCriterion criterion(
            Long id,
            ReviewRound criterionRound,
            String code,
            int maxScore,
            int sortOrder,
            boolean active
    ) {
        ReviewCriterion result = ReviewCriterion.builder()
                .reviewRound(criterionRound)
                .code(code)
                .label(code)
                .maxScore(maxScore)
                .sortOrder(sortOrder)
                .active(active)
                .build();
        ReflectionTestUtils.setField(result, "id", id);
        return result;
    }

    private ReviewRoundEntry entry(
            Long id,
            ReviewRound entryRound,
            ReviewRoundEntryStatus status
    ) {
        ReviewRoundEntry result = ReviewRoundEntry.builder()
                .reviewRound(entryRound)
                .status(status)
                .build();
        ReflectionTestUtils.setField(result, "id", id);
        return result;
    }

    private ReviewAssignment assignment(
            Long id,
            ContestJudge assignmentJudge,
            ReviewRoundEntry assignmentEntry,
            ReviewAssignmentStatus status,
            LocalDateTime dueAt
    ) {
        ReviewAssignment result = ReviewAssignment.builder()
                .contestJudge(assignmentJudge)
                .reviewRoundEntry(assignmentEntry)
                .status(status)
                .assignedAt(NOW.minusHours(1))
                .dueAt(dueAt)
                .completedAt(
                        status == ReviewAssignmentStatus.COMPLETED
                                ? NOW.minusMinutes(1)
                                : null
                )
                .build();
        ReflectionTestUtils.setField(result, "id", id);
        return result;
    }

    private Review review(
            Long id,
            ReviewAssignment reviewAssignment,
            String totalScore,
            String comment,
            LocalDateTime submittedAt
    ) {
        Review result = Review.builder()
                .assignment(reviewAssignment)
                .totalScore(new BigDecimal(totalScore))
                .comment(comment)
                .submittedAt(submittedAt)
                .build();
        ReflectionTestUtils.setField(result, "id", id);
        return result;
    }

    private ReviewScoreItem reviewScoreItem(
            Long id,
            Review review,
            ReviewCriterion criterion,
            String score
    ) {
        ReviewScoreItem result = ReviewScoreItem.builder()
                .review(review)
                .reviewCriterion(criterion)
                .score(new BigDecimal(score))
                .build();
        ReflectionTestUtils.setField(result, "id", id);
        return result;
    }

    private static Stream<Arguments> notOpenRoundCases() {
        return Stream.of(
                Arguments.of(
                        "PREPARING 상태",
                        ReviewRoundStatus.PREPARING,
                        NOW.minusHours(1),
                        NOW.plusHours(1)
                ),
                Arguments.of(
                        "시작 1초 전",
                        ReviewRoundStatus.OPEN,
                        NOW.plusSeconds(1),
                        NOW.plusHours(1)
                ),
                Arguments.of(
                        "종료 시각 경계",
                        ReviewRoundStatus.OPEN,
                        NOW.minusHours(1),
                        NOW
                )
        );
    }

    private static Stream<Arguments> invalidScoreCases() {
        return Stream.of(
                Arguments.of("0 미만 점수", "-0.01"),
                Arguments.of("최대 점수 초과", "40.01"),
                Arguments.of("소수 셋째 자리", "1.001")
        );
    }
}
