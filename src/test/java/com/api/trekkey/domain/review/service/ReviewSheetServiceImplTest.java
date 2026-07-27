package com.api.trekkey.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStage;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.ReviewCriterion;
import com.api.trekkey.domain.contest.entity.StageStatus;
import com.api.trekkey.domain.contest.entity.StageType;
import com.api.trekkey.domain.contest.repository.ReviewCriterionRepository;
import com.api.trekkey.domain.review.entity.ContestJudge;
import com.api.trekkey.domain.review.entity.ReviewAssignment;
import com.api.trekkey.domain.review.entity.ReviewAssignmentStatus;
import com.api.trekkey.domain.review.entity.ReviewRoundEntry;
import com.api.trekkey.domain.review.entity.ReviewRoundEntryStatus;
import com.api.trekkey.domain.review.repository.ReviewAssignmentRepository;
import com.api.trekkey.domain.review.support.ReviewLinkAuthenticator;
import com.api.trekkey.domain.review.web.dto.request.ReviewAccessReq;
import com.api.trekkey.domain.review.web.dto.response.ReviewSheetAssignmentRes;
import com.api.trekkey.domain.review.web.dto.response.ReviewSheetCriterionRes;
import com.api.trekkey.domain.review.web.dto.response.ReviewSheetRes;
import com.api.trekkey.domain.review.web.dto.response.ReviewSheetStageRes;
import com.api.trekkey.domain.submission.entity.Submission;
import com.api.trekkey.domain.submission.entity.SubmissionStatus;
import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReviewSheetServiceImplTest {

    private static final String RAW_TOKEN_A = "a".repeat(43);
    private static final String RAW_TOKEN_B = "b".repeat(43);
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 7, 24, 12, 0);

    @Mock
    private ReviewLinkAuthenticator reviewLinkAuthenticator;

    @Mock
    private ReviewAssignmentRepository reviewAssignmentRepository;

    @Mock
    private ReviewCriterionRepository reviewCriterionRepository;

    private ReviewSheetServiceImpl service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                NOW.atZone(ZONE_ID).toInstant(),
                ZONE_ID
        );
        service = new ReviewSheetServiceImpl(
                reviewLinkAuthenticator,
                reviewAssignmentRepository,
                reviewCriterionRepository,
                clock
        );
    }

    @Test
    @DisplayName("요청 값이 아닌 인증된 심사위원 ID로만 평가표를 조회한다")
    void getReviewSheet_queriesByAuthenticatedJudgeOnly() {
        Contest contest = contest(100L, ContestStatus.REVIEWING);
        ContestJudge judge = judge(200L, contest, "김심사");
        ContestStage stage = openStage(300L, contest, 1);
        ReviewAssignment assignment =
                assignment(400L, judge, stage, "submission-a");
        ReviewAccessReq request = new ReviewAccessReq(RAW_TOKEN_A);

        given(reviewLinkAuthenticator.authenticate(RAW_TOKEN_A, NOW))
                .willReturn(judge);
        given(reviewAssignmentRepository.findAllWithDetailsByJudgeId(200L))
                .willReturn(List.of(assignment));
        given(reviewCriterionRepository
                .findAllByContestStageIdInOrderBySortOrderAsc(List.of(300L)))
                .willReturn(List.of());

        ReviewSheetRes response = service.getReviewSheet(request);

        assertThat(response.stages()).hasSize(1);
        assertThat(response.stages().getFirst().assignments())
                .extracting(ReviewSheetAssignmentRes::submissionPublicId)
                .containsExactly("submission-a");
        verify(reviewLinkAuthenticator).authenticate(RAW_TOKEN_A, NOW);
        verify(reviewAssignmentRepository)
                .findAllWithDetailsByJudgeId(200L);
    }

    @Test
    @DisplayName("서로 다른 심사위원의 토큰은 각자 배정된 제출물만 조회한다")
    void getReviewSheet_isolatesAssignmentsByJudge() {
        Contest contest = contest(100L, ContestStatus.REVIEWING);
        ContestJudge judgeA = judge(201L, contest, "김심사");
        ContestJudge judgeB = judge(202L, contest, "이심사");
        ContestStage stage = openStage(300L, contest, 1);
        ReviewAssignment assignmentA =
                assignment(401L, judgeA, stage, "submission-a");
        ReviewAssignment assignmentB =
                assignment(402L, judgeB, stage, "submission-b");

        given(reviewLinkAuthenticator.authenticate(RAW_TOKEN_A, NOW))
                .willReturn(judgeA);
        given(reviewLinkAuthenticator.authenticate(RAW_TOKEN_B, NOW))
                .willReturn(judgeB);
        given(reviewAssignmentRepository.findAllWithDetailsByJudgeId(201L))
                .willReturn(List.of(assignmentA));
        given(reviewAssignmentRepository.findAllWithDetailsByJudgeId(202L))
                .willReturn(List.of(assignmentB));
        given(reviewCriterionRepository
                .findAllByContestStageIdInOrderBySortOrderAsc(List.of(300L)))
                .willReturn(List.of());

        ReviewSheetRes responseA =
                service.getReviewSheet(new ReviewAccessReq(RAW_TOKEN_A));
        ReviewSheetRes responseB =
                service.getReviewSheet(new ReviewAccessReq(RAW_TOKEN_B));

        assertThat(responseA.stages().getFirst().assignments())
                .extracting(ReviewSheetAssignmentRes::submissionPublicId)
                .containsExactly("submission-a")
                .doesNotContain("submission-b");
        assertThat(responseB.stages().getFirst().assignments())
                .extracting(ReviewSheetAssignmentRes::submissionPublicId)
                .containsExactly("submission-b")
                .doesNotContain("submission-a");
        verify(reviewAssignmentRepository)
                .findAllWithDetailsByJudgeId(201L);
        verify(reviewAssignmentRepository)
                .findAllWithDetailsByJudgeId(202L);
    }

    @Test
    @DisplayName("대회가 심사 중이 아니면 배정 저장소를 조회하지 않고 빈 평가표를 반환한다")
    void getReviewSheet_returnsEmptyWhenContestIsNotReviewing() {
        Contest contest = contest(100L, ContestStatus.PREPARING);
        ContestJudge judge = judge(200L, contest, "김심사");
        given(reviewLinkAuthenticator.authenticate(RAW_TOKEN_A, NOW))
                .willReturn(judge);

        ReviewSheetRes response =
                service.getReviewSheet(new ReviewAccessReq(RAW_TOKEN_A));

        assertThat(response.judgeName()).isEqualTo("김심사");
        assertThat(response.contestPublicId()).isEqualTo("contest-public-id");
        assertThat(response.stages()).isEmpty();
        verifyNoInteractions(
                reviewAssignmentRepository,
                reviewCriterionRepository
        );
    }

    @Test
    @DisplayName("OPEN 상태이고 실제 시작과 종료 시각 안에 있는 단계만 노출한다")
    void getReviewSheet_filtersStagesByActualOpenWindow() {
        Contest contest = contest(100L, ContestStatus.REVIEWING);
        ContestJudge judge = judge(200L, contest, "김심사");
        ContestStage visibleStage = stage(
                301L,
                contest,
                1,
                StageStatus.OPEN,
                NOW,
                NOW.plusHours(1)
        );
        ContestStage futureStage = stage(
                302L,
                contest,
                2,
                StageStatus.OPEN,
                NOW.plusSeconds(1),
                NOW.plusHours(2)
        );
        ContestStage endedAtBoundaryStage = stage(
                303L,
                contest,
                3,
                StageStatus.OPEN,
                NOW.minusHours(2),
                NOW
        );
        ContestStage preparingStage = stage(
                304L,
                contest,
                4,
                StageStatus.PREPARING,
                NOW.minusHours(1),
                NOW.plusHours(1)
        );
        given(reviewLinkAuthenticator.authenticate(RAW_TOKEN_A, NOW))
                .willReturn(judge);
        given(reviewAssignmentRepository.findAllWithDetailsByJudgeId(200L))
                .willReturn(List.of(
                        assignment(401L, judge, futureStage, "future"),
                        assignment(
                                402L,
                                judge,
                                endedAtBoundaryStage,
                                "ended"
                        ),
                        assignment(
                                403L,
                                judge,
                                preparingStage,
                                "preparing"
                        ),
                        assignment(404L, judge, visibleStage, "visible")
                ));
        given(reviewCriterionRepository
                .findAllByContestStageIdInOrderBySortOrderAsc(List.of(301L)))
                .willReturn(List.of());

        ReviewSheetRes response =
                service.getReviewSheet(new ReviewAccessReq(RAW_TOKEN_A));

        assertThat(response.stages())
                .extracting(ReviewSheetStageRes::reviewStageId)
                .containsExactly(301L);
        assertThat(response.stages().getFirst().assignments())
                .extracting(ReviewSheetAssignmentRes::submissionPublicId)
                .containsExactly("visible");
        verify(reviewCriterionRepository)
                .findAllByContestStageIdInOrderBySortOrderAsc(List.of(301L));
    }

    @Test
    @DisplayName("배정 상태와 마감 시각에 따라 평가 가능한 제출물만 노출한다")
    void getReviewSheet_filtersAssignmentsByStatusAndDueAt() {
        Contest contest = contest(100L, ContestStatus.REVIEWING);
        ContestJudge judge = judge(200L, contest, "김심사");
        ContestStage stage = openStage(300L, contest, 1);
        ReviewAssignment available =
                assignment(401L, judge, stage, "available");
        ReviewAssignment withoutDueAt =
                assignment(402L, judge, stage, "without-due-at");
        ReflectionTestUtils.setField(withoutDueAt, "dueAt", null);
        ReviewAssignment expiredAtBoundary =
                assignment(403L, judge, stage, "expired-at-boundary");
        ReflectionTestUtils.setField(expiredAtBoundary, "dueAt", NOW);
        ReviewAssignment canceled =
                assignment(404L, judge, stage, "canceled");
        ReflectionTestUtils.setField(
                canceled,
                "status",
                ReviewAssignmentStatus.CANCELED
        );
        ReviewAssignment completed =
                assignment(405L, judge, stage, "completed");
        ReflectionTestUtils.setField(
                completed,
                "status",
                ReviewAssignmentStatus.COMPLETED
        );
        ReflectionTestUtils.setField(
                completed,
                "completedAt",
                NOW.minusMinutes(10)
        );
        ReflectionTestUtils.setField(
                completed,
                "dueAt",
                NOW.minusMinutes(5)
        );

        given(reviewLinkAuthenticator.authenticate(RAW_TOKEN_A, NOW))
                .willReturn(judge);
        given(reviewAssignmentRepository.findAllWithDetailsByJudgeId(200L))
                .willReturn(List.of(
                        available,
                        withoutDueAt,
                        expiredAtBoundary,
                        canceled,
                        completed
                ));
        given(reviewCriterionRepository
                .findAllByContestStageIdInOrderBySortOrderAsc(List.of(300L)))
                .willReturn(List.of());

        ReviewSheetRes response =
                service.getReviewSheet(new ReviewAccessReq(RAW_TOKEN_A));

        assertThat(response.stages().getFirst().assignments())
                .extracting(ReviewSheetAssignmentRes::submissionPublicId)
                .containsExactly(
                        "available",
                        "without-due-at",
                        "completed"
                )
                .doesNotContain(
                        "expired-at-boundary",
                        "canceled"
                );
    }

    @Test
    @DisplayName("활성 평가 기준만 단계별로 묶고 표시 순서대로 반환한다")
    void getReviewSheet_groupsOnlyActiveCriteriaByStage() {
        Contest contest = contest(100L, ContestStatus.REVIEWING);
        ContestJudge judge = judge(200L, contest, "김심사");
        ContestStage firstStage = openStage(301L, contest, 1);
        ContestStage secondStage = openStage(302L, contest, 2);
        given(reviewLinkAuthenticator.authenticate(RAW_TOKEN_A, NOW))
                .willReturn(judge);
        given(reviewAssignmentRepository.findAllWithDetailsByJudgeId(200L))
                .willReturn(List.of(
                        assignment(401L, judge, firstStage, "submission-a"),
                        assignment(402L, judge, secondStage, "submission-b")
                ));
        given(reviewCriterionRepository
                .findAllByContestStageIdInOrderBySortOrderAsc(
                        List.of(301L, 302L)
                ))
                .willReturn(List.of(
                        criterion(
                                503L,
                                secondStage,
                                "impact",
                                "파급력",
                                2,
                                true
                        ),
                        criterion(
                                501L,
                                firstStage,
                                "feasibility",
                                "실현 가능성",
                                2,
                                true
                        ),
                        criterion(
                                504L,
                                secondStage,
                                "inactive",
                                "미사용 기준",
                                1,
                                false
                        ),
                        criterion(
                                500L,
                                firstStage,
                                "creativity",
                                "창의성",
                                1,
                                true
                        )
                ));

        ReviewSheetRes response =
                service.getReviewSheet(new ReviewAccessReq(RAW_TOKEN_A));

        assertThat(response.stages())
                .extracting(ReviewSheetStageRes::reviewStageId)
                .containsExactly(301L, 302L);
        assertThat(response.stages().get(0).criteria())
                .extracting(ReviewSheetCriterionRes::code)
                .containsExactly("creativity", "feasibility");
        assertThat(response.stages().get(1).criteria())
                .extracting(ReviewSheetCriterionRes::code)
                .containsExactly("impact")
                .doesNotContain("inactive");
    }

    @Test
    @DisplayName("심사위원 평가표 응답 모델은 팀·사용자·토큰과 공식 결과를 노출하지 않는다")
    void getReviewSheet_responseModelDoesNotExposeSensitiveFields() {
        Contest contest = contest(100L, ContestStatus.REVIEWING);
        ContestJudge judge = judge(200L, contest, "김심사");
        ContestStage stage = openStage(300L, contest, 1);
        ReviewAssignment assignment =
                assignment(400L, judge, stage, "submission-a");
        given(reviewLinkAuthenticator.authenticate(RAW_TOKEN_A, NOW))
                .willReturn(judge);
        given(reviewAssignmentRepository.findAllWithDetailsByJudgeId(200L))
                .willReturn(List.of(assignment));
        given(reviewCriterionRepository
                .findAllByContestStageIdInOrderBySortOrderAsc(List.of(300L)))
                .willReturn(List.of(criterion(
                        500L,
                        stage,
                        "creativity",
                        "창의성",
                        1,
                        true
                )));

        ReviewSheetRes response =
                service.getReviewSheet(new ReviewAccessReq(RAW_TOKEN_A));

        Set<String> responseFieldNames = Stream.of(
                        ReviewSheetRes.class,
                        ReviewSheetStageRes.class,
                        ReviewSheetAssignmentRes.class,
                        ReviewSheetCriterionRes.class
                )
                .flatMap(type -> Stream.of(type.getRecordComponents()))
                .map(RecordComponent::getName)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(responseFieldNames).doesNotContain(
                "team",
                "teamId",
                "teamName",
                "user",
                "userId",
                "token",
                "rawToken",
                "reviewToken",
                "reviewTokenHash",
                "tokenHash",
                "finalScore",
                "rank",
                "rankNo",
                "decisionReason"
        );
        assertThat(response.toString())
                .doesNotContain(RAW_TOKEN_A)
                .doesNotContain("reviewTokenHash")
                .doesNotContain("finalScore")
                .doesNotContain("decisionReason");
    }

    @Test
    @DisplayName("배정이 없으면 기준을 조회하지 않고 심사위원 정보가 포함된 빈 평가표를 반환한다")
    void getReviewSheet_returnsEmptySheetWhenNoAssignmentsExist() {
        Contest contest = contest(100L, ContestStatus.REVIEWING);
        ContestJudge judge = judge(200L, contest, "김심사");
        given(reviewLinkAuthenticator.authenticate(RAW_TOKEN_A, NOW))
                .willReturn(judge);
        given(reviewAssignmentRepository.findAllWithDetailsByJudgeId(200L))
                .willReturn(List.of());

        ReviewSheetRes response =
                service.getReviewSheet(new ReviewAccessReq(RAW_TOKEN_A));

        assertThat(response.judgeName()).isEqualTo("김심사");
        assertThat(response.roleLabel()).isEqualTo("외부 전문가");
        assertThat(response.contestPublicId()).isEqualTo("contest-public-id");
        assertThat(response.contestTitle()).isEqualTo("AI 공모전");
        assertThat(response.tokenExpiresAt()).isEqualTo(NOW.plusDays(1));
        assertThat(response.stages()).isEmpty();
        verify(reviewCriterionRepository, never())
                .findAllByContestStageIdInOrderBySortOrderAsc(
                        org.mockito.ArgumentMatchers.anyCollection()
                );
    }

    private Contest contest(Long id, ContestStatus status) {
        Contest contest = Contest.builder()
                .publicId("contest-public-id")
                .title("AI 공모전")
                .status(status)
                .build();
        ReflectionTestUtils.setField(contest, "id", id);
        return contest;
    }

    private ContestJudge judge(
            Long id,
            Contest contest,
            String name
    ) {
        ContestJudge judge = ContestJudge.builder()
                .contest(contest)
                .name(name)
                .roleLabel("외부 전문가")
                .tokenExpiresAt(NOW.plusDays(1))
                .build();
        ReflectionTestUtils.setField(judge, "id", id);
        return judge;
    }

    private ContestStage openStage(
            Long id,
            Contest contest,
            int sequenceNo
    ) {
        return stage(
                id,
                contest,
                sequenceNo,
                StageStatus.OPEN,
                NOW.minusHours(1),
                NOW.plusHours(1)
        );
    }

    private ContestStage stage(
            Long id,
            Contest contest,
            int sequenceNo,
            StageStatus status,
            LocalDateTime startsAt,
            LocalDateTime endsAt
    ) {
        ContestStage stage = ContestStage.builder()
                .contest(contest)
                .name(sequenceNo + "차 심사")
                .stageType(StageType.REVIEW)
                .sequenceNo(sequenceNo)
                .status(status)
                .startsAt(startsAt)
                .endsAt(endsAt)
                .build();
        ReflectionTestUtils.setField(stage, "id", id);
        return stage;
    }

    private ReviewAssignment assignment(
            Long id,
            ContestJudge judge,
            ContestStage stage,
            String submissionPublicId
    ) {
        Submission submission = Submission.builder()
                .publicId(submissionPublicId)
                .title(submissionPublicId + " 제목")
                .status(SubmissionStatus.SUBMITTED)
                .build();
        ReviewRoundEntry entry = ReviewRoundEntry.builder()
                .reviewStage(stage)
                .submission(submission)
                .status(ReviewRoundEntryStatus.IN_REVIEW)
                .build();
        return ReviewAssignment.builder()
                .id(id)
                .contestJudge(judge)
                .reviewRoundEntry(entry)
                .status(ReviewAssignmentStatus.ASSIGNED)
                .assignedAt(NOW.minusDays(1))
                .dueAt(NOW.plusHours(1))
                .build();
    }

    private ReviewCriterion criterion(
            Long id,
            ContestStage stage,
            String code,
            String label,
            int sortOrder,
            boolean active
    ) {
        return ReviewCriterion.builder()
                .id(id)
                .contestStage(stage)
                .code(code)
                .label(label)
                .maxScore(10)
                .sortOrder(sortOrder)
                .active(active)
                .build();
    }
}
