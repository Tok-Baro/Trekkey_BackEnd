package com.api.trekkey.domain.review.publicapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStage;
import com.api.trekkey.domain.contest.entity.ReviewCriterion;
import com.api.trekkey.domain.contest.entity.StageStatus;
import com.api.trekkey.domain.contest.repository.ReviewCriterionRepository;
import com.api.trekkey.domain.review.entity.AssignmentStatus;
import com.api.trekkey.domain.review.entity.ContestJudge;
import com.api.trekkey.domain.review.entity.ContestStageEntry;
import com.api.trekkey.domain.review.entity.Review;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.entity.ReviewAssignment;
import com.api.trekkey.domain.review.publicapi.web.dto.ReviewSubmitReq;
import com.api.trekkey.domain.review.repository.ContestJudgeRepository;
import com.api.trekkey.domain.review.repository.ReviewAssignmentRepository;
import com.api.trekkey.domain.review.repository.ReviewRepository;
import com.api.trekkey.domain.review.repository.ReviewScoreItemRepository;
import com.api.trekkey.domain.review.support.ReviewTokenSupport;
import com.api.trekkey.domain.submission.repository.SubmissionFileRepository;
import com.api.trekkey.domain.submission.support.FileStoragePort;
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
class JudgeReviewServiceImplTest {

    private static final String RAW_TOKEN = "raw-token";

    @Mock
    private ContestJudgeRepository contestJudgeRepository;

    @Mock
    private ReviewAssignmentRepository assignmentRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewScoreItemRepository scoreItemRepository;

    @Mock
    private ReviewCriterionRepository criterionRepository;

    @Mock
    private SubmissionFileRepository submissionFileRepository;

    @Mock
    private FileStoragePort fileStoragePort;

    private JudgeReviewServiceImpl judgeReviewService;

    private final ReviewTokenSupport tokenSupport = new ReviewTokenSupport("http://localhost:5173");

    private ContestJudge judge;
    private ContestStage stage;
    private ReviewAssignment assignment;

    @BeforeEach
    void setUp() {
        judgeReviewService = new JudgeReviewServiceImpl(
                contestJudgeRepository, assignmentRepository, reviewRepository, scoreItemRepository,
                criterionRepository, submissionFileRepository, fileStoragePort, tokenSupport);

        judge = mock(ContestJudge.class);
        lenient().when(judge.getId()).thenReturn(10L);
        lenient().when(judge.isTokenExpired(any())).thenReturn(false);
        lenient().when(contestJudgeRepository.findByReviewTokenHash(tokenSupport.hash(RAW_TOKEN)))
                .thenReturn(Optional.of(judge));

        stage = mock(ContestStage.class);
        lenient().when(stage.getId()).thenReturn(300L);
        lenient().when(stage.getStatus()).thenReturn(StageStatus.OPEN);

        ContestStageEntry entry = mock(ContestStageEntry.class);
        lenient().when(entry.getContestStage()).thenReturn(stage);

        assignment = ReviewAssignment.builder()
                .contestJudge(judge)
                .contestStageEntry(entry)
                .status(AssignmentStatus.ASSIGNED)
                .assignedAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(assignment, "id", 40L);
        lenient().when(assignmentRepository.findById(40L)).thenReturn(Optional.of(assignment));

        lenient().when(reviewRepository.save(any(Review.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(scoreItemRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("심사 제출 시 총점이 계산되고 배정이 완료 처리된다")
    void submitReview_savesReviewAndCompletesAssignment() {
        givenCriteria();

        judgeReviewService.submitReview(RAW_TOKEN, 40L, new ReviewSubmitReq("좋은 작품입니다", List.of(
                new ReviewSubmitReq.ScoreReq(1L, new BigDecimal("25")),
                new ReviewSubmitReq.ScoreReq(2L, new BigDecimal("40")))));

        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.COMPLETED);
        assertThat(assignment.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("평가 기준이 하나라도 누락되면 제출할 수 없다")
    void submitReview_throwsWhenCriterionMismatch() {
        givenCriteria();

        assertThatThrownBy(() -> judgeReviewService.submitReview(RAW_TOKEN, 40L,
                new ReviewSubmitReq(null, List.of(
                        new ReviewSubmitReq.ScoreReq(1L, new BigDecimal("25"))))))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode.SCORE_CRITERION_MISMATCH);
    }

    @Test
    @DisplayName("배점을 초과한 점수는 제출할 수 없다")
    void submitReview_throwsWhenScoreOutOfRange() {
        givenCriteria();

        assertThatThrownBy(() -> judgeReviewService.submitReview(RAW_TOKEN, 40L,
                new ReviewSubmitReq(null, List.of(
                        new ReviewSubmitReq.ScoreReq(1L, new BigDecimal("31")),
                        new ReviewSubmitReq.ScoreReq(2L, new BigDecimal("40"))))))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode.SCORE_OUT_OF_RANGE);
    }

    @Test
    @DisplayName("이미 제출한 심사는 다시 제출할 수 없다")
    void submitReview_throwsWhenAlreadySubmitted() {
        given(reviewRepository.existsByAssignmentId(40L)).willReturn(true);

        assertThatThrownBy(() -> judgeReviewService.submitReview(RAW_TOKEN, 40L,
                new ReviewSubmitReq(null, List.of(
                        new ReviewSubmitReq.ScoreReq(1L, new BigDecimal("25"))))))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode.REVIEW_ALREADY_SUBMITTED);
    }

    @Test
    @DisplayName("마감된 라운드에는 심사를 제출할 수 없다")
    void submitReview_throwsWhenRoundNotOpen() {
        given(stage.getStatus()).willReturn(StageStatus.COMPLETED);

        assertThatThrownBy(() -> judgeReviewService.submitReview(RAW_TOKEN, 40L,
                new ReviewSubmitReq(null, List.of(
                        new ReviewSubmitReq.ScoreReq(1L, new BigDecimal("25"))))))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode.ROUND_NOT_OPEN);
    }

    @Test
    @DisplayName("만료된 심사 링크는 거부한다")
    void submitReview_throwsWhenTokenExpired() {
        given(judge.isTokenExpired(any())).willReturn(true);

        assertThatThrownBy(() -> judgeReviewService.getPortal(RAW_TOKEN))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode.REVIEW_TOKEN_EXPIRED);
    }

    //======= 헬퍼 메서드 ==========

    private void givenCriteria() {
        Contest contest = mock(Contest.class);
        ReviewCriterion first = criterionFixture(1L, "창의성", 30);
        ReviewCriterion second = criterionFixture(2L, "완성도", 40);
        lenient().when(criterionRepository.findAllByContestStageIdInOrderBySortOrderAsc(List.of(300L)))
                .thenReturn(List.of(first, second));
    }

    private ReviewCriterion criterionFixture(Long id, String label, int maxScore) {
        ReviewCriterion criterion = ReviewCriterion.builder()
                .contestStage(stage)
                .code("criterion-" + id)
                .label(label)
                .maxScore(maxScore)
                .sortOrder(id.intValue())
                .active(true)
                .build();
        ReflectionTestUtils.setField(criterion, "id", id);
        return criterion;
    }
}
