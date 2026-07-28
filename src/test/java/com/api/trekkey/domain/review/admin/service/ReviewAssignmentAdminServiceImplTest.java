package com.api.trekkey.domain.review.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.api.trekkey.domain.audit.entity.AuditAction;
import com.api.trekkey.domain.audit.support.AdminAuditLogger;
import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.ParticipationType;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.review.entity.ContestJudge;
import com.api.trekkey.domain.review.entity.ReviewAssignment;
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
import com.api.trekkey.domain.review.admin.web.dto.request.ReviewAssignmentDueAtReq;
import com.api.trekkey.domain.review.admin.web.dto.request.ReviewAssignmentPrepareReq;
import com.api.trekkey.domain.review.admin.web.dto.response.ReviewAssignmentRes;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReviewAssignmentAdminServiceImplTest {

    private static final Long ADMIN_ID = 10L;
    private static final Long ORGANIZATION_ID = 1L;
    private static final Long CONTEST_ID = 100L;
    private static final Long REVIEW_ROUND_ID = 200L;
    private static final Long JUDGE_ID = 300L;
    private static final String CONTEST_PUBLIC_ID = "contest-public-id";
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 7, 24, 12, 0);
    private static final LocalDateTime ROUND_ENDS_AT =
            LocalDateTime.of(2026, 7, 25, 12, 0);

    @Mock
    private UserRepository userRepository;

    @Mock
    private ContestRepository contestRepository;

    @Mock
    private ReviewRoundRepository reviewRoundRepository;

    @Mock
    private ContestJudgeRepository contestJudgeRepository;

    @Mock
    private ReviewRoundEntryRepository reviewRoundEntryRepository;

    @Mock
    private ReviewAssignmentRepository reviewAssignmentRepository;

    @Mock
    private AdminAuditLogger adminAuditLogger;

    private ReviewAssignmentAdminServiceImpl service;
    private Organization organization;
    private User admin;
    private Contest contest;
    private ContestJudge judge;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-24T03:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        service = new ReviewAssignmentAdminServiceImpl(
                userRepository,
                contestRepository,
                reviewRoundRepository,
                contestJudgeRepository,
                reviewRoundEntryRepository,
                reviewAssignmentRepository,
                adminAuditLogger,
                clock
        );

        organization = org.mockito.Mockito.mock(Organization.class);
        org.mockito.Mockito.lenient()
                .when(organization.getId())
                .thenReturn(ORGANIZATION_ID);
        admin = user(ADMIN_ID, UserRole.ADMIN, UserStatus.ACTIVE);
        contest = contest(CONTEST_ID, ContestStatus.REVIEWING);
        judge = ContestJudge.builder()
                .contest(contest)
                .name("김심사")
                .roleLabel("외부 전문가")
                .build();
        ReflectionTestUtils.setField(judge, "id", JUDGE_ID);
    }

    @Test
    @DisplayName("PREPARING 라운드의 모든 엔트리를 한 심사위원에게 배정하고 잠금 순서와 감사 로그를 지킨다")
    @SuppressWarnings("unchecked")
    void prepareAssignments_createsAssignmentsForAllEntriesInLockOrder() {
        ReviewRound round = reviewRound(ReviewRoundStatus.PREPARING);
        List<ReviewRoundEntry> entries = List.of(
                entry(401L, round, ReviewRoundEntryStatus.ELIGIBLE),
                entry(402L, round, ReviewRoundEntryStatus.IN_REVIEW)
        );
        LocalDateTime dueAt = NOW.plusHours(2);
        List<ReviewAssignment> persisted = new ArrayList<>();
        stubLockedContext(round, entries, List.of());
        given(reviewAssignmentRepository.saveAllAndFlush(anyList()))
                .willAnswer(invocation -> {
                    List<ReviewAssignment> assignments =
                            invocation.getArgument(0);
                    for (int index = 0; index < assignments.size(); index++) {
                        ReflectionTestUtils.setField(
                                assignments.get(index),
                                "id",
                                501L + index
                        );
                    }
                    persisted.addAll(assignments);
                    return assignments;
                });
        given(reviewAssignmentRepository
                .findAllWithDetailsByJudgeIdAndReviewRoundId(
                        JUDGE_ID,
                        REVIEW_ROUND_ID
                )).willAnswer(invocation -> persisted);

        List<ReviewAssignmentRes> response = service.prepareAssignments(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                REVIEW_ROUND_ID,
                JUDGE_ID,
                new ReviewAssignmentPrepareReq(dueAt)
        );

        assertThat(response).hasSize(2);
        assertThat(response)
                .extracting(ReviewAssignmentRes::reviewRoundEntryId)
                .containsExactly(401L, 402L);
        assertThat(response)
                .extracting(ReviewAssignmentRes::status)
                .containsOnly(ReviewAssignmentStatus.ASSIGNED);

        ArgumentCaptor<List<ReviewAssignment>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(reviewAssignmentRepository)
                .saveAllAndFlush(captor.capture());
        assertThat(captor.getValue()).hasSize(2).allSatisfy(assignment -> {
            assertThat(assignment.getContestJudge()).isSameAs(judge);
            assertThat(assignment.getStatus())
                    .isEqualTo(ReviewAssignmentStatus.ASSIGNED);
            assertThat(assignment.getAssignedAt()).isEqualTo(NOW);
            assertThat(assignment.getDueAt()).isEqualTo(dueAt);
        });
        assertThat(entries)
                .extracting(ReviewRoundEntry::getStatus)
                .containsExactly(
                        ReviewRoundEntryStatus.ELIGIBLE,
                        ReviewRoundEntryStatus.IN_REVIEW
                );

        InOrder lockOrder = inOrder(
                contestJudgeRepository,
                reviewRoundRepository,
                reviewRoundEntryRepository,
                reviewAssignmentRepository
        );
        lockOrder.verify(contestJudgeRepository)
                .findByIdAndContestIdForUpdate(JUDGE_ID, CONTEST_ID);
        lockOrder.verify(reviewRoundRepository)
                .findByIdForShare(REVIEW_ROUND_ID);
        lockOrder.verify(reviewRoundEntryRepository)
                .findAllForShareByReviewRoundIdOrderByIdAsc(
                        REVIEW_ROUND_ID);
        lockOrder.verify(reviewAssignmentRepository)
                .findAllForUpdateByJudgeIdAndEntryIdIn(
                        JUDGE_ID,
                        List.of(401L, 402L)
                );
        lockOrder.verify(reviewAssignmentRepository)
                .saveAllAndFlush(anyList());
        verify(adminAuditLogger).log(
                ADMIN_ID,
                ORGANIZATION_ID,
                AuditAction.REVIEW_ASSIGNMENTS_PREPARE,
                "REVIEW_ROUND",
                REVIEW_ROUND_ID,
                "judgeId=300, assignmentCount=2"
        );
    }

    @Test
    @DisplayName("모든 엔트리가 이미 배정되었으면 저장과 감사 로그 없이 기존 결과를 반환한다")
    void prepareAssignments_isIdempotentWhenAllAssignmentsExist() {
        ReviewRound round = reviewRound(ReviewRoundStatus.PREPARING);
        ReviewRoundEntry first =
                entry(401L, round, ReviewRoundEntryStatus.ELIGIBLE);
        ReviewRoundEntry second =
                entry(402L, round, ReviewRoundEntryStatus.IN_REVIEW);
        ReviewAssignment assigned = assignment(
                501L,
                first,
                ReviewAssignmentStatus.ASSIGNED,
                NOW.minusHours(1),
                ROUND_ENDS_AT
        );
        ReviewAssignment completed = assignment(
                502L,
                second,
                ReviewAssignmentStatus.COMPLETED,
                NOW.minusHours(1),
                ROUND_ENDS_AT
        );
        stubLockedContext(
                round,
                List.of(first, second),
                List.of(assigned, completed)
        );

        List<ReviewAssignmentRes> response = service.prepareAssignments(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                REVIEW_ROUND_ID,
                JUDGE_ID,
                new ReviewAssignmentPrepareReq(ROUND_ENDS_AT)
        );

        assertThat(response)
                .extracting(ReviewAssignmentRes::id)
                .containsExactly(501L, 502L);
        verify(reviewAssignmentRepository, never())
                .saveAllAndFlush(anyList());
        verifyNoInteractions(adminAuditLogger);
    }

    @Test
    @DisplayName("취소된 배정만 같은 행으로 재활성화하고 변경 건수만 감사 로그에 기록한다")
    @SuppressWarnings("unchecked")
    void prepareAssignments_reactivatesOnlyCanceledAssignment() {
        ReviewRound round = reviewRound(ReviewRoundStatus.PREPARING);
        ReviewRoundEntry first =
                entry(401L, round, ReviewRoundEntryStatus.ELIGIBLE);
        ReviewRoundEntry second =
                entry(402L, round, ReviewRoundEntryStatus.ELIGIBLE);
        ReviewAssignment active = assignment(
                501L,
                first,
                ReviewAssignmentStatus.ASSIGNED,
                NOW.minusDays(1),
                ROUND_ENDS_AT
        );
        ReviewAssignment canceled = assignment(
                502L,
                second,
                ReviewAssignmentStatus.CANCELED,
                NOW.minusDays(1),
                NOW.minusHours(1)
        );
        ReflectionTestUtils.setField(
                canceled,
                "completedAt",
                NOW.minusHours(2)
        );
        LocalDateTime newDueAt = NOW.plusHours(4);
        stubLockedContext(
                round,
                List.of(first, second),
                List.of(active, canceled)
        );
        given(reviewAssignmentRepository.saveAllAndFlush(anyList()))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(reviewAssignmentRepository
                .findAllWithDetailsByJudgeIdAndReviewRoundId(
                        JUDGE_ID,
                        REVIEW_ROUND_ID
                )).willReturn(List.of(active, canceled));

        service.prepareAssignments(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                REVIEW_ROUND_ID,
                JUDGE_ID,
                new ReviewAssignmentPrepareReq(newDueAt)
        );

        ArgumentCaptor<List<ReviewAssignment>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(reviewAssignmentRepository)
                .saveAllAndFlush(captor.capture());
        assertThat(captor.getValue()).containsExactly(canceled);
        assertThat(canceled.getStatus())
                .isEqualTo(ReviewAssignmentStatus.ASSIGNED);
        assertThat(canceled.getAssignedAt()).isEqualTo(NOW);
        assertThat(canceled.getDueAt()).isEqualTo(newDueAt);
        assertThat(canceled.getCompletedAt()).isNull();
        assertThat(active.getAssignedAt()).isEqualTo(NOW.minusDays(1));
        verify(adminAuditLogger).log(
                ADMIN_ID,
                ORGANIZATION_ID,
                AuditAction.REVIEW_ASSIGNMENTS_PREPARE,
                "REVIEW_ROUND",
                REVIEW_ROUND_ID,
                "judgeId=300, assignmentCount=1"
        );
    }

    @Test
    @DisplayName("OPEN 라운드에도 배정할 수 있고 라운드 종료 시각과 같은 마감 시각을 허용한다")
    void prepareAssignments_allowsOpenRoundAndDeadlineAtRoundEnd() {
        ReviewRound round = reviewRound(ReviewRoundStatus.OPEN);
        ReviewRoundEntry entry =
                entry(401L, round, ReviewRoundEntryStatus.IN_REVIEW);
        List<ReviewAssignment> persisted = new ArrayList<>();
        stubLockedContext(round, List.of(entry), List.of());
        given(reviewAssignmentRepository.saveAllAndFlush(anyList()))
                .willAnswer(invocation -> {
                    List<ReviewAssignment> assignments =
                            invocation.getArgument(0);
                    ReflectionTestUtils.setField(
                            assignments.getFirst(),
                            "id",
                            501L
                    );
                    persisted.addAll(assignments);
                    return assignments;
                });
        given(reviewAssignmentRepository
                .findAllWithDetailsByJudgeIdAndReviewRoundId(
                        JUDGE_ID,
                        REVIEW_ROUND_ID
                )).willAnswer(invocation -> persisted);

        List<ReviewAssignmentRes> response = service.prepareAssignments(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                REVIEW_ROUND_ID,
                JUDGE_ID,
                new ReviewAssignmentPrepareReq(ROUND_ENDS_AT)
        );

        assertThat(response).singleElement().satisfies(assignment -> {
            assertThat(assignment.status())
                    .isEqualTo(ReviewAssignmentStatus.ASSIGNED);
            assertThat(assignment.dueAt()).isEqualTo(ROUND_ENDS_AT);
        });
    }

    @Test
    @DisplayName("FINALIZED 라운드에는 새 배정을 만들 수 없다")
    void prepareAssignments_rejectsFinalizedRound() {
        ReviewRound round = reviewRound(ReviewRoundStatus.FINALIZED);
        ReviewRoundEntry entry =
                entry(401L, round, ReviewRoundEntryStatus.IN_REVIEW);
        stubLockedContext(round, List.of(entry), List.of());

        assertCode(
                () -> service.prepareAssignments(
                        ADMIN_ID,
                        CONTEST_PUBLIC_ID,
                        REVIEW_ROUND_ID,
                        JUDGE_ID,
                        new ReviewAssignmentPrepareReq(ROUND_ENDS_AT)
                ),
                ReviewErrorResponseCode
                        .REVIEW_ASSIGNMENT_PREPARATION_NOT_ALLOWED
        );

        verify(reviewAssignmentRepository, never())
                .saveAllAndFlush(anyList());
        verifyNoInteractions(adminAuditLogger);
    }

    @Test
    @DisplayName("심사 없는 수동 라운드에는 심사위원을 배정할 수 없다")
    void prepareAssignments_rejectsManualRoundWithoutReview() {
        ReviewRound round = reviewRound(ReviewRoundStatus.PREPARING);
        ReflectionTestUtils.setField(
                round,
                "targetType",
                ReviewRoundTargetType.MANUAL);
        ReflectionTestUtils.setField(
                round,
                "decisionRule",
                ReviewRoundDecisionRule.MANUAL);
        ReflectionTestUtils.setField(round, "selectCount", null);
        ReviewRoundEntry entry =
                entry(401L, round, ReviewRoundEntryStatus.ELIGIBLE);
        stubLockedContext(round, List.of(entry), List.of());

        assertCode(
                () -> service.prepareAssignments(
                        ADMIN_ID,
                        CONTEST_PUBLIC_ID,
                        REVIEW_ROUND_ID,
                        JUDGE_ID,
                        new ReviewAssignmentPrepareReq(ROUND_ENDS_AT)
                ),
                ReviewErrorResponseCode
                        .REVIEW_ASSIGNMENT_PREPARATION_NOT_ALLOWED
        );

        verify(reviewAssignmentRepository, never())
                .saveAllAndFlush(anyList());
        verifyNoInteractions(adminAuditLogger);
    }

    @Test
    @DisplayName("배정 가능 여부는 기존 대회 상태가 아니라 리뷰 라운드 상태로 판단한다")
    void prepareAssignments_doesNotDependOnLegacyContestStatus() {
        ReflectionTestUtils.setField(
                contest,
                "status",
                ContestStatus.APPLICATION_OPEN
        );
        ReviewRound round = reviewRound(ReviewRoundStatus.PREPARING);
        ReviewRoundEntry entry =
                entry(401L, round, ReviewRoundEntryStatus.ELIGIBLE);
        ReviewAssignment existing = assignment(
                501L,
                entry,
                ReviewAssignmentStatus.ASSIGNED,
                NOW.minusHours(1),
                ROUND_ENDS_AT
        );
        stubLockedContext(round, List.of(entry), List.of(existing));

        List<ReviewAssignmentRes> response = service.prepareAssignments(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                REVIEW_ROUND_ID,
                JUDGE_ID,
                new ReviewAssignmentPrepareReq(ROUND_ENDS_AT)
        );

        assertThat(response).extracting(ReviewAssignmentRes::id)
                .containsExactly(501L);
        verify(reviewAssignmentRepository, never())
                .saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("마감 시각이 현재와 같으면 배정을 거부한다")
    void prepareAssignments_rejectsDeadlineAtNow() {
        ReviewRound round = reviewRound(ReviewRoundStatus.PREPARING);
        ReviewRoundEntry entry =
                entry(401L, round, ReviewRoundEntryStatus.ELIGIBLE);
        stubLockedContext(round, List.of(entry), List.of());

        assertCode(
                () -> service.prepareAssignments(
                        ADMIN_ID,
                        CONTEST_PUBLIC_ID,
                        REVIEW_ROUND_ID,
                        JUDGE_ID,
                        new ReviewAssignmentPrepareReq(NOW)
                ),
                ReviewErrorResponseCode.REVIEW_ASSIGNMENT_DUE_AT_INVALID
        );

        verify(reviewAssignmentRepository, never())
                .saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("마감 시각이 라운드 종료 시각보다 늦으면 배정을 거부한다")
    void prepareAssignments_rejectsDeadlineAfterRoundEnd() {
        ReviewRound round = reviewRound(ReviewRoundStatus.PREPARING);
        ReviewRoundEntry entry =
                entry(401L, round, ReviewRoundEntryStatus.ELIGIBLE);
        stubLockedContext(round, List.of(entry), List.of());

        assertCode(
                () -> service.prepareAssignments(
                        ADMIN_ID,
                        CONTEST_PUBLIC_ID,
                        REVIEW_ROUND_ID,
                        JUDGE_ID,
                        new ReviewAssignmentPrepareReq(
                                ROUND_ENDS_AT.plusNanos(1)
                        )
                ),
                ReviewErrorResponseCode.REVIEW_ASSIGNMENT_DUE_AT_INVALID
        );

        verify(reviewAssignmentRepository, never())
                .saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("심사 엔트리가 없으면 배정을 거부한다")
    void prepareAssignments_rejectsEmptyEntries() {
        ReviewRound round = reviewRound(ReviewRoundStatus.PREPARING);
        stubAdminContestAndRoundOrganization();
        given(contestJudgeRepository
                .findByIdAndContestIdForUpdate(JUDGE_ID, CONTEST_ID))
                .willReturn(Optional.of(judge));
        given(reviewRoundRepository.findByIdForShare(REVIEW_ROUND_ID))
                .willReturn(Optional.of(round));
        given(reviewRoundEntryRepository
                .findAllForShareByReviewRoundIdOrderByIdAsc(
                        REVIEW_ROUND_ID))
                .willReturn(List.of());

        assertCode(
                () -> service.prepareAssignments(
                        ADMIN_ID,
                        CONTEST_PUBLIC_ID,
                        REVIEW_ROUND_ID,
                        JUDGE_ID,
                        new ReviewAssignmentPrepareReq(ROUND_ENDS_AT)
                ),
                ReviewErrorResponseCode.REVIEW_ASSIGNMENT_ENTRY_REQUIRED
        );

        verifyNoInteractions(
                reviewAssignmentRepository,
                adminAuditLogger
        );
    }

    @Test
    @DisplayName("선정 완료 등 terminal 상태의 엔트리가 포함되면 배정을 거부한다")
    void prepareAssignments_rejectsTerminalEntry() {
        ReviewRound round = reviewRound(ReviewRoundStatus.PREPARING);
        ReviewRoundEntry entry =
                entry(401L, round, ReviewRoundEntryStatus.SELECTED);
        stubLockedContext(round, List.of(entry), List.of());

        assertCode(
                () -> service.prepareAssignments(
                        ADMIN_ID,
                        CONTEST_PUBLIC_ID,
                        REVIEW_ROUND_ID,
                        JUDGE_ID,
                        new ReviewAssignmentPrepareReq(ROUND_ENDS_AT)
                ),
                ReviewErrorResponseCode.REVIEW_ASSIGNMENT_ENTRY_INVALID
        );

        verify(reviewAssignmentRepository, never())
                .saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("요청 대회에 속하지 않는 심사위원은 찾을 수 없는 것으로 처리한다")
    void prepareAssignments_rejectsJudgeOutsideContest() {
        stubAdminContestAndRoundOrganization();
        given(contestJudgeRepository
                .findByIdAndContestIdForUpdate(JUDGE_ID, CONTEST_ID))
                .willReturn(Optional.empty());

        assertCode(
                () -> service.prepareAssignments(
                        ADMIN_ID,
                        CONTEST_PUBLIC_ID,
                        REVIEW_ROUND_ID,
                        JUDGE_ID,
                        new ReviewAssignmentPrepareReq(ROUND_ENDS_AT)
                ),
                ReviewErrorResponseCode.CONTEST_JUDGE_NOT_FOUND
        );

        verify(reviewRoundRepository, never())
                .findByIdForShare(REVIEW_ROUND_ID);
        verifyNoInteractions(
                reviewRoundEntryRepository,
                reviewAssignmentRepository,
                adminAuditLogger
        );
    }

    @Test
    @DisplayName("요청 대회와 다른 대회의 리뷰 라운드는 찾을 수 없는 것으로 처리한다")
    void prepareAssignments_rejectsRoundOutsideContest() {
        Contest otherContest =
                contest(999L, ContestStatus.REVIEWING);
        ReviewRound otherRound =
                reviewRound(otherContest, ReviewRoundStatus.PREPARING);
        stubAdminContestAndRoundOrganization();
        given(contestJudgeRepository
                .findByIdAndContestIdForUpdate(JUDGE_ID, CONTEST_ID))
                .willReturn(Optional.of(judge));
        given(reviewRoundRepository.findByIdForShare(REVIEW_ROUND_ID))
                .willReturn(Optional.of(otherRound));

        assertCode(
                () -> service.prepareAssignments(
                        ADMIN_ID,
                        CONTEST_PUBLIC_ID,
                        REVIEW_ROUND_ID,
                        JUDGE_ID,
                        new ReviewAssignmentPrepareReq(ROUND_ENDS_AT)
                ),
                ReviewErrorResponseCode.REVIEW_ROUND_NOT_FOUND
        );

        verifyNoInteractions(
                reviewRoundEntryRepository,
                reviewAssignmentRepository,
                adminAuditLogger
        );
    }

    @Test
    @DisplayName("비활성 관리자는 심사위원 배정 API를 사용할 수 없다")
    void prepareAssignments_rejectsInactiveAdmin() {
        ReflectionTestUtils.setField(admin, "status", UserStatus.INACTIVE);
        given(userRepository.findById(ADMIN_ID))
                .willReturn(Optional.of(admin));

        assertCode(
                () -> service.prepareAssignments(
                        ADMIN_ID,
                        CONTEST_PUBLIC_ID,
                        REVIEW_ROUND_ID,
                        JUDGE_ID,
                        new ReviewAssignmentPrepareReq(ROUND_ENDS_AT)
                ),
                UserErrorResponseCode.USER_INVALID_TOKEN
        );

        verifyNoInteractions(
                contestRepository,
                reviewRoundRepository,
                contestJudgeRepository,
                reviewRoundEntryRepository,
                reviewAssignmentRepository,
                adminAuditLogger
        );
    }

    @Test
    @DisplayName("DB 복합 유니크 제약 충돌은 배정 중복 도메인 오류로 변환한다")
    void prepareAssignments_mapsUniqueViolationToDomainError() {
        ReviewRound round = reviewRound(ReviewRoundStatus.PREPARING);
        ReviewRoundEntry entry =
                entry(401L, round, ReviewRoundEntryStatus.ELIGIBLE);
        stubLockedContext(round, List.of(entry), List.of());
        given(reviewAssignmentRepository.saveAllAndFlush(anyList()))
                .willThrow(new DataIntegrityViolationException("duplicate"));

        assertCode(
                () -> service.prepareAssignments(
                        ADMIN_ID,
                        CONTEST_PUBLIC_ID,
                        REVIEW_ROUND_ID,
                        JUDGE_ID,
                        new ReviewAssignmentPrepareReq(ROUND_ENDS_AT)
                ),
                ReviewErrorResponseCode.REVIEW_ASSIGNMENT_DUPLICATED
        );

        verifyNoInteractions(adminAuditLogger);
        verify(reviewAssignmentRepository, never())
                .findAllWithDetailsByJudgeIdAndReviewRoundId(
                        JUDGE_ID,
                        REVIEW_ROUND_ID
                );
    }

    @Test
    @DisplayName("ASSIGNED 배정을 취소하고 잠금 순서와 감사 로그를 지킨다")
    void cancelAssignment_cancelsAssignedInLockOrder() {
        ReviewRound round = reviewRound(ReviewRoundStatus.OPEN);
        ReviewRoundEntry entry =
                entry(401L, round, ReviewRoundEntryStatus.IN_REVIEW);
        ReviewAssignment assignment = assignment(
                501L,
                entry,
                ReviewAssignmentStatus.ASSIGNED,
                NOW.minusHours(1),
                ROUND_ENDS_AT
        );
        stubManagementContext(round, assignment);

        ReviewAssignmentRes response = service.cancelAssignment(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                REVIEW_ROUND_ID,
                JUDGE_ID,
                501L
        );

        assertThat(response.status())
                .isEqualTo(ReviewAssignmentStatus.CANCELED);
        InOrder lockOrder = inOrder(
                contestJudgeRepository,
                reviewRoundRepository,
                reviewAssignmentRepository
        );
        lockOrder.verify(contestJudgeRepository)
                .findByIdAndContestIdForUpdate(JUDGE_ID, CONTEST_ID);
        lockOrder.verify(reviewRoundRepository)
                .findByIdForShare(REVIEW_ROUND_ID);
        lockOrder.verify(reviewAssignmentRepository)
                .findByIdAndJudgeIdAndReviewRoundIdForUpdate(
                        501L,
                        JUDGE_ID,
                        REVIEW_ROUND_ID
                );
        lockOrder.verify(reviewAssignmentRepository).flush();
        verify(adminAuditLogger).log(
                ADMIN_ID,
                ORGANIZATION_ID,
                AuditAction.REVIEW_ASSIGNMENT_CANCEL,
                "REVIEW_ASSIGNMENT",
                501L,
                "judgeId=300, reviewRoundId=200"
        );
    }

    @Test
    @DisplayName("COMPLETED 배정은 취소할 수 없다")
    void cancelAssignment_rejectsCompletedAssignment() {
        ReviewRound round = reviewRound(ReviewRoundStatus.OPEN);
        ReviewAssignment completed = assignment(
                501L,
                entry(401L, round, ReviewRoundEntryStatus.IN_REVIEW),
                ReviewAssignmentStatus.COMPLETED,
                NOW.minusHours(2),
                ROUND_ENDS_AT
        );
        stubManagementContext(round, completed);

        assertCode(
                () -> service.cancelAssignment(
                        ADMIN_ID,
                        CONTEST_PUBLIC_ID,
                        REVIEW_ROUND_ID,
                        JUDGE_ID,
                        501L
                ),
                ReviewErrorResponseCode
                        .REVIEW_ASSIGNMENT_STATUS_TRANSITION_INVALID
        );

        verify(reviewAssignmentRepository, never()).flush();
        verifyNoInteractions(adminAuditLogger);
    }

    @Test
    @DisplayName("CANCELED 배정을 새 마감 시각으로 재배정한다")
    void reassignAssignment_reactivatesCanceledAssignment() {
        ReviewRound round = reviewRound(ReviewRoundStatus.OPEN);
        ReviewAssignment canceled = assignment(
                501L,
                entry(401L, round, ReviewRoundEntryStatus.IN_REVIEW),
                ReviewAssignmentStatus.CANCELED,
                NOW.minusHours(2),
                NOW.minusHours(1)
        );
        LocalDateTime dueAt = NOW.plusHours(5);
        stubManagementContext(round, canceled);

        ReviewAssignmentRes response = service.reassignAssignment(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                REVIEW_ROUND_ID,
                JUDGE_ID,
                501L,
                new ReviewAssignmentDueAtReq(dueAt)
        );

        assertThat(response.status())
                .isEqualTo(ReviewAssignmentStatus.ASSIGNED);
        assertThat(response.assignedAt()).isEqualTo(NOW);
        assertThat(response.dueAt()).isEqualTo(dueAt);
        verify(reviewAssignmentRepository).flush();
        verify(adminAuditLogger).log(
                ADMIN_ID,
                ORGANIZATION_ID,
                AuditAction.REVIEW_ASSIGNMENT_REASSIGN,
                "REVIEW_ASSIGNMENT",
                501L,
                "judgeId=300, reviewRoundId=200, dueAt=" + dueAt
        );
    }

    @Test
    @DisplayName("ASSIGNED 배정의 마감 시각을 변경한다")
    void updateDueAt_updatesAssignedDeadline() {
        ReviewRound round = reviewRound(ReviewRoundStatus.OPEN);
        ReviewAssignment assigned = assignment(
                501L,
                entry(401L, round, ReviewRoundEntryStatus.IN_REVIEW),
                ReviewAssignmentStatus.ASSIGNED,
                NOW.minusHours(1),
                NOW.plusHours(1)
        );
        LocalDateTime dueAt = NOW.plusHours(6);
        stubManagementContext(round, assigned);

        ReviewAssignmentRes response = service.updateDueAt(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                REVIEW_ROUND_ID,
                JUDGE_ID,
                501L,
                new ReviewAssignmentDueAtReq(dueAt)
        );

        assertThat(response.dueAt()).isEqualTo(dueAt);
        assertThat(response.status())
                .isEqualTo(ReviewAssignmentStatus.ASSIGNED);
        verify(reviewAssignmentRepository).flush();
        verify(adminAuditLogger).log(
                ADMIN_ID,
                ORGANIZATION_ID,
                AuditAction.REVIEW_ASSIGNMENT_DUE_AT_UPDATE,
                "REVIEW_ASSIGNMENT",
                501L,
                "judgeId=300, reviewRoundId=200, dueAt=" + dueAt
        );
    }

    @Test
    @DisplayName("라운드 종료 이후로 배정 마감을 변경할 수 없다")
    void updateDueAt_rejectsDeadlineAfterRoundEnd() {
        ReviewRound round = reviewRound(ReviewRoundStatus.OPEN);
        ReviewAssignment assigned = assignment(
                501L,
                entry(401L, round, ReviewRoundEntryStatus.IN_REVIEW),
                ReviewAssignmentStatus.ASSIGNED,
                NOW.minusHours(1),
                NOW.plusHours(1)
        );
        stubManagementContext(round, assigned);

        assertCode(
                () -> service.updateDueAt(
                        ADMIN_ID,
                        CONTEST_PUBLIC_ID,
                        REVIEW_ROUND_ID,
                        JUDGE_ID,
                        501L,
                        new ReviewAssignmentDueAtReq(
                                ROUND_ENDS_AT.plusNanos(1))
                ),
                ReviewErrorResponseCode.REVIEW_ASSIGNMENT_DUE_AT_INVALID
        );

        assertThat(assigned.getDueAt()).isEqualTo(NOW.plusHours(1));
        verify(reviewAssignmentRepository, never()).flush();
        verifyNoInteractions(adminAuditLogger);
    }

    @Test
    @DisplayName("FINALIZED 라운드의 배정은 변경할 수 없다")
    void cancelAssignment_rejectsFinalizedRound() {
        ReviewRound round = reviewRound(ReviewRoundStatus.FINALIZED);
        ReviewAssignment assigned = assignment(
                501L,
                entry(401L, round, ReviewRoundEntryStatus.SELECTED),
                ReviewAssignmentStatus.ASSIGNED,
                NOW.minusHours(1),
                ROUND_ENDS_AT
        );
        stubAdminContestAndRoundOrganization();
        given(contestJudgeRepository
                .findByIdAndContestIdForUpdate(JUDGE_ID, CONTEST_ID))
                .willReturn(Optional.of(judge));
        given(reviewRoundRepository.findByIdForShare(REVIEW_ROUND_ID))
                .willReturn(Optional.of(round));

        assertCode(
                () -> service.cancelAssignment(
                        ADMIN_ID,
                        CONTEST_PUBLIC_ID,
                        REVIEW_ROUND_ID,
                        JUDGE_ID,
                        501L
                ),
                ReviewErrorResponseCode
                        .REVIEW_ASSIGNMENT_MANAGEMENT_NOT_ALLOWED
        );

        verify(reviewAssignmentRepository, never())
                .findByIdAndJudgeIdAndReviewRoundIdForUpdate(
                        501L,
                        JUDGE_ID,
                        REVIEW_ROUND_ID
                );
        assertThat(assigned.getStatus())
                .isEqualTo(ReviewAssignmentStatus.ASSIGNED);
    }

    private void stubManagementContext(
            ReviewRound round,
            ReviewAssignment assignment
    ) {
        stubAdminContestAndRoundOrganization();
        given(contestJudgeRepository
                .findByIdAndContestIdForUpdate(JUDGE_ID, CONTEST_ID))
                .willReturn(Optional.of(judge));
        given(reviewRoundRepository.findByIdForShare(REVIEW_ROUND_ID))
                .willReturn(Optional.of(round));
        given(reviewAssignmentRepository
                .findByIdAndJudgeIdAndReviewRoundIdForUpdate(
                        assignment.getId(),
                        JUDGE_ID,
                        REVIEW_ROUND_ID
                )).willReturn(Optional.of(assignment));
    }

    private void stubLockedContext(
            ReviewRound round,
            List<ReviewRoundEntry> entries,
            List<ReviewAssignment> existingAssignments
    ) {
        stubAdminContestAndRoundOrganization();
        given(contestJudgeRepository
                .findByIdAndContestIdForUpdate(JUDGE_ID, CONTEST_ID))
                .willReturn(Optional.of(judge));
        given(reviewRoundRepository.findByIdForShare(REVIEW_ROUND_ID))
                .willReturn(Optional.of(round));
        given(reviewRoundEntryRepository
                .findAllForShareByReviewRoundIdOrderByIdAsc(
                        REVIEW_ROUND_ID))
                .willReturn(entries);
        given(reviewAssignmentRepository
                .findAllForUpdateByJudgeIdAndEntryIdIn(
                        JUDGE_ID,
                        entries.stream()
                                .map(ReviewRoundEntry::getId)
                                .toList()
                )).willReturn(existingAssignments);
    }

    private void stubAdminContestAndRoundOrganization() {
        given(userRepository.findById(ADMIN_ID))
                .willReturn(Optional.of(admin));
        given(contestRepository.findByPublicId(CONTEST_PUBLIC_ID))
                .willReturn(Optional.of(contest));
        given(reviewRoundRepository
                .findOrganizationIdById(REVIEW_ROUND_ID))
                .willReturn(Optional.of(ORGANIZATION_ID));
    }

    private ReviewRound reviewRound(ReviewRoundStatus status) {
        return reviewRound(contest, status);
    }

    private ReviewRound reviewRound(
            Contest roundContest,
            ReviewRoundStatus status
    ) {
        ReviewRound round = ReviewRound.builder()
                .contest(roundContest)
                .roundNo(1)
                .name("본선 심사")
                .status(status)
                .startsAt(NOW.minusHours(1))
                .endsAt(ROUND_ENDS_AT)
                .targetType(ReviewRoundTargetType.ALL_SUBMISSIONS)
                .decisionRule(ReviewRoundDecisionRule.TOP_N)
                .selectCount(1)
                .build();
        ReflectionTestUtils.setField(round, "id", REVIEW_ROUND_ID);
        return round;
    }

    private ReviewRoundEntry entry(
            Long id,
            ReviewRound round,
            ReviewRoundEntryStatus status
    ) {
        Team team = Team.builder()
                .contest(contest)
                .leaderUser(admin)
                .name("트랙키 팀 " + id)
                .leaderName("홍길동")
                .major("컴퓨터공학부")
                .memberCount(1)
                .status(TeamStatus.APPROVED)
                .contactEmail("team" + id + "@example.com")
                .phone("010-1234-5678")
                .motivation("지원 동기")
                .build();
        ReflectionTestUtils.setField(team, "id", id + 1000);
        Submission submission = Submission.builder()
                .publicId("submission-" + id)
                .team(team)
                .title("작품 " + id)
                .status(SubmissionStatus.SUBMITTED)
                .submittedAt(NOW.minusDays(1))
                .finalizedAt(NOW.minusHours(1))
                .build();
        ReflectionTestUtils.setField(submission, "id", id + 2000);
        ReviewRoundEntry entry = ReviewRoundEntry.builder()
                .reviewRound(round)
                .submission(submission)
                .status(status)
                .build();
        ReflectionTestUtils.setField(entry, "id", id);
        return entry;
    }

    private ReviewAssignment assignment(
            Long id,
            ReviewRoundEntry entry,
            ReviewAssignmentStatus status,
            LocalDateTime assignedAt,
            LocalDateTime dueAt
    ) {
        ReviewAssignment assignment = ReviewAssignment.builder()
                .contestJudge(judge)
                .reviewRoundEntry(entry)
                .status(status)
                .assignedAt(assignedAt)
                .dueAt(dueAt)
                .completedAt(
                        status == ReviewAssignmentStatus.COMPLETED
                                ? NOW.minusMinutes(30)
                                : null
                )
                .build();
        ReflectionTestUtils.setField(assignment, "id", id);
        return assignment;
    }

    private User user(
            Long id,
            UserRole role,
            UserStatus status
    ) {
        User user = User.builder()
                .organization(organization)
                .name("관리자")
                .email("admin@example.com")
                .password("encoded")
                .role(role)
                .memberType(MemberType.STAFF)
                .status(status)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Contest contest(Long id, ContestStatus status) {
        Contest contest = Contest.builder()
                .publicId(CONTEST_PUBLIC_ID + "-" + id)
                .organization(organization)
                .ownerUser(admin)
                .title("AI 공모전")
                .department("교무처")
                .status(status)
                .participationType(ParticipationType.TEAM)
                .awardCount(1)
                .summary("AI 공모전")
                .target("재학생")
                .applicationMethod("온라인")
                .benefits("상장")
                .detailHtml("<p>본문</p>")
                .build();
        ReflectionTestUtils.setField(contest, "id", id);
        if (id.equals(CONTEST_ID)) {
            ReflectionTestUtils.setField(
                    contest,
                    "publicId",
                    CONTEST_PUBLIC_ID
            );
        }
        return contest;
    }

    private void assertCode(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
            Object expectedCode
    ) {
        assertThatThrownBy(callable)
                .isInstanceOf(CustomException.class)
                .extracting(exception ->
                        ((CustomException) exception)
                                .getBaseResponseCode())
                .isEqualTo(expectedCode);
    }
}
