package com.api.trekkey.domain.review.service;

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
import com.api.trekkey.domain.contest.entity.ContestStage;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.ParticipationType;
import com.api.trekkey.domain.contest.entity.StagePassRule;
import com.api.trekkey.domain.contest.entity.StageStatus;
import com.api.trekkey.domain.contest.entity.StageTargetType;
import com.api.trekkey.domain.contest.entity.StageType;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.contest.repository.ContestStageRepository;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.review.entity.ContestJudge;
import com.api.trekkey.domain.review.entity.ReviewAssignment;
import com.api.trekkey.domain.review.entity.ReviewAssignmentStatus;
import com.api.trekkey.domain.review.entity.ReviewRoundEntry;
import com.api.trekkey.domain.review.entity.ReviewRoundEntryStatus;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.repository.ContestJudgeRepository;
import com.api.trekkey.domain.review.repository.ReviewAssignmentRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundEntryRepository;
import com.api.trekkey.domain.review.web.dto.request.ReviewAssignmentPrepareReq;
import com.api.trekkey.domain.review.web.dto.response.ReviewAssignmentRes;
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
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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
    private static final Long REVIEW_STAGE_ID = 200L;
    private static final Long JUDGE_ID = 300L;
    private static final String CONTEST_PUBLIC_ID = "contest-public-id";
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 7, 24, 12, 0);
    private static final LocalDateTime STAGE_ENDS_AT =
            LocalDateTime.of(2026, 7, 25, 12, 0);

    @Mock
    private UserRepository userRepository;

    @Mock
    private ContestRepository contestRepository;

    @Mock
    private ContestStageRepository contestStageRepository;

    @Mock
    private ContestJudgeRepository contestJudgeRepository;

    @Mock
    private ReviewRoundEntryRepository reviewRoundEntryRepository;

    @Mock
    private ReviewAssignmentRepository reviewAssignmentRepository;

    @Mock
    private AdminAuditLogger adminAuditLogger;

    @Mock
    private EntityManager entityManager;

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
                contestStageRepository,
                contestJudgeRepository,
                reviewRoundEntryRepository,
                reviewAssignmentRepository,
                adminAuditLogger,
                clock,
                entityManager
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
    @DisplayName("PREPARING 단계의 모든 엔트리를 한 심사위원에게 배정하고 잠금 순서와 감사 로그를 지킨다")
    @SuppressWarnings("unchecked")
    void prepareAssignments_createsAssignmentsForAllEntriesInLockOrder() {
        ContestStage stage = reviewStage(StageStatus.PREPARING);
        List<ReviewRoundEntry> entries = List.of(
                entry(401L, stage, ReviewRoundEntryStatus.ELIGIBLE),
                entry(402L, stage, ReviewRoundEntryStatus.IN_REVIEW)
        );
        LocalDateTime dueAt = NOW.plusHours(2);
        List<ReviewAssignment> persisted = new ArrayList<>();
        stubLockedContext(stage, entries, List.of());
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
                .findAllWithDetailsByJudgeIdAndReviewStageId(
                        JUDGE_ID,
                        REVIEW_STAGE_ID
                )).willAnswer(invocation -> persisted);

        List<ReviewAssignmentRes> response = service.prepareAssignments(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                REVIEW_STAGE_ID,
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
                contestStageRepository,
                reviewRoundEntryRepository,
                reviewAssignmentRepository
        );
        lockOrder.verify(contestJudgeRepository)
                .findByIdAndContestIdForUpdate(JUDGE_ID, CONTEST_ID);
        lockOrder.verify(contestStageRepository)
                .findByIdForShare(REVIEW_STAGE_ID);
        lockOrder.verify(reviewRoundEntryRepository)
                .findAllForShareByReviewStageIdOrderByIdAsc(
                        REVIEW_STAGE_ID);
        lockOrder.verify(reviewAssignmentRepository)
                .findAllForUpdateByJudgeIdAndEntryIdIn(
                        JUDGE_ID,
                        List.of(401L, 402L)
                );
        lockOrder.verify(reviewAssignmentRepository)
                .saveAllAndFlush(anyList());
        verify(entityManager).refresh(
                contest,
                LockModeType.PESSIMISTIC_READ
        );
        verify(adminAuditLogger).log(
                ADMIN_ID,
                ORGANIZATION_ID,
                AuditAction.REVIEW_ASSIGNMENTS_PREPARE,
                "REVIEW_STAGE",
                REVIEW_STAGE_ID,
                "judgeId=300, assignmentCount=2"
        );
    }

    @Test
    @DisplayName("모든 엔트리가 이미 배정되었으면 저장과 감사 로그 없이 기존 결과를 반환한다")
    void prepareAssignments_isIdempotentWhenAllAssignmentsExist() {
        ContestStage stage = reviewStage(StageStatus.PREPARING);
        ReviewRoundEntry first =
                entry(401L, stage, ReviewRoundEntryStatus.ELIGIBLE);
        ReviewRoundEntry second =
                entry(402L, stage, ReviewRoundEntryStatus.IN_REVIEW);
        ReviewAssignment assigned = assignment(
                501L,
                first,
                ReviewAssignmentStatus.ASSIGNED,
                NOW.minusHours(1),
                STAGE_ENDS_AT
        );
        ReviewAssignment completed = assignment(
                502L,
                second,
                ReviewAssignmentStatus.COMPLETED,
                NOW.minusHours(1),
                STAGE_ENDS_AT
        );
        stubLockedContext(
                stage,
                List.of(first, second),
                List.of(assigned, completed)
        );
        given(reviewAssignmentRepository
                .findAllWithDetailsByJudgeIdAndReviewStageId(
                        JUDGE_ID,
                        REVIEW_STAGE_ID
                )).willReturn(List.of(assigned, completed));

        List<ReviewAssignmentRes> response = service.prepareAssignments(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                REVIEW_STAGE_ID,
                JUDGE_ID,
                new ReviewAssignmentPrepareReq(STAGE_ENDS_AT)
        );

        assertThat(response)
                .extracting(ReviewAssignmentRes::id)
                .containsExactly(501L, 502L);
        verify(reviewAssignmentRepository, never())
                .saveAllAndFlush(anyList());
        verifyNoInteractions(adminAuditLogger, entityManager);
    }

    @Test
    @DisplayName("취소된 배정만 같은 행으로 재활성화하고 변경 건수만 감사 로그에 기록한다")
    @SuppressWarnings("unchecked")
    void prepareAssignments_reactivatesOnlyCanceledAssignment() {
        ContestStage stage = reviewStage(StageStatus.PREPARING);
        ReviewRoundEntry first =
                entry(401L, stage, ReviewRoundEntryStatus.ELIGIBLE);
        ReviewRoundEntry second =
                entry(402L, stage, ReviewRoundEntryStatus.ELIGIBLE);
        ReviewAssignment active = assignment(
                501L,
                first,
                ReviewAssignmentStatus.ASSIGNED,
                NOW.minusDays(1),
                STAGE_ENDS_AT
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
                stage,
                List.of(first, second),
                List.of(active, canceled)
        );
        given(reviewAssignmentRepository.saveAllAndFlush(anyList()))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(reviewAssignmentRepository
                .findAllWithDetailsByJudgeIdAndReviewStageId(
                        JUDGE_ID,
                        REVIEW_STAGE_ID
                )).willReturn(List.of(active, canceled));

        service.prepareAssignments(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                REVIEW_STAGE_ID,
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
                "REVIEW_STAGE",
                REVIEW_STAGE_ID,
                "judgeId=300, assignmentCount=1"
        );
    }

    @Test
    @DisplayName("OPEN 단계에도 배정할 수 있고 단계 종료 시각과 같은 마감 시각을 허용한다")
    void prepareAssignments_allowsOpenStageAndDeadlineAtStageEnd() {
        ContestStage stage = reviewStage(StageStatus.OPEN);
        ReviewRoundEntry entry =
                entry(401L, stage, ReviewRoundEntryStatus.IN_REVIEW);
        List<ReviewAssignment> persisted = new ArrayList<>();
        stubLockedContext(stage, List.of(entry), List.of());
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
                .findAllWithDetailsByJudgeIdAndReviewStageId(
                        JUDGE_ID,
                        REVIEW_STAGE_ID
                )).willAnswer(invocation -> persisted);

        List<ReviewAssignmentRes> response = service.prepareAssignments(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                REVIEW_STAGE_ID,
                JUDGE_ID,
                new ReviewAssignmentPrepareReq(STAGE_ENDS_AT)
        );

        assertThat(response).singleElement().satisfies(assignment -> {
            assertThat(assignment.status())
                    .isEqualTo(ReviewAssignmentStatus.ASSIGNED);
            assertThat(assignment.dueAt()).isEqualTo(STAGE_ENDS_AT);
        });
    }

    @Test
    @DisplayName("COMPLETED 단계에는 새 배정을 만들 수 없다")
    void prepareAssignments_rejectsCompletedStage() {
        ContestStage stage = reviewStage(StageStatus.COMPLETED);
        ReviewRoundEntry entry =
                entry(401L, stage, ReviewRoundEntryStatus.IN_REVIEW);
        stubLockedContext(stage, List.of(entry), List.of());

        assertCode(
                () -> service.prepareAssignments(
                        ADMIN_ID,
                        CONTEST_PUBLIC_ID,
                        REVIEW_STAGE_ID,
                        JUDGE_ID,
                        new ReviewAssignmentPrepareReq(STAGE_ENDS_AT)
                ),
                ReviewErrorResponseCode
                        .REVIEW_ASSIGNMENT_PREPARATION_NOT_ALLOWED
        );

        verify(reviewAssignmentRepository, never())
                .saveAllAndFlush(anyList());
        verifyNoInteractions(adminAuditLogger, entityManager);
    }

    @Test
    @DisplayName("대회가 REVIEWING 상태가 아니면 새 배정을 만들 수 없다")
    void prepareAssignments_rejectsContestNotReviewing() {
        ReflectionTestUtils.setField(
                contest,
                "status",
                ContestStatus.APPLICATION_OPEN
        );
        ContestStage stage = reviewStage(StageStatus.PREPARING);
        ReviewRoundEntry entry =
                entry(401L, stage, ReviewRoundEntryStatus.ELIGIBLE);
        stubLockedContext(stage, List.of(entry), List.of());

        assertCode(
                () -> service.prepareAssignments(
                        ADMIN_ID,
                        CONTEST_PUBLIC_ID,
                        REVIEW_STAGE_ID,
                        JUDGE_ID,
                        new ReviewAssignmentPrepareReq(STAGE_ENDS_AT)
                ),
                ReviewErrorResponseCode
                        .REVIEW_ENTRY_CONTEST_NOT_REVIEWING
        );

        verify(entityManager).refresh(
                contest,
                LockModeType.PESSIMISTIC_READ
        );
        verify(reviewAssignmentRepository, never())
                .saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("마감 시각이 현재와 같으면 배정을 거부한다")
    void prepareAssignments_rejectsDeadlineAtNow() {
        ContestStage stage = reviewStage(StageStatus.PREPARING);
        ReviewRoundEntry entry =
                entry(401L, stage, ReviewRoundEntryStatus.ELIGIBLE);
        stubLockedContext(stage, List.of(entry), List.of());

        assertCode(
                () -> service.prepareAssignments(
                        ADMIN_ID,
                        CONTEST_PUBLIC_ID,
                        REVIEW_STAGE_ID,
                        JUDGE_ID,
                        new ReviewAssignmentPrepareReq(NOW)
                ),
                ReviewErrorResponseCode.REVIEW_ASSIGNMENT_DUE_AT_INVALID
        );

        verify(reviewAssignmentRepository, never())
                .saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("마감 시각이 단계 종료 시각보다 늦으면 배정을 거부한다")
    void prepareAssignments_rejectsDeadlineAfterStageEnd() {
        ContestStage stage = reviewStage(StageStatus.PREPARING);
        ReviewRoundEntry entry =
                entry(401L, stage, ReviewRoundEntryStatus.ELIGIBLE);
        stubLockedContext(stage, List.of(entry), List.of());

        assertCode(
                () -> service.prepareAssignments(
                        ADMIN_ID,
                        CONTEST_PUBLIC_ID,
                        REVIEW_STAGE_ID,
                        JUDGE_ID,
                        new ReviewAssignmentPrepareReq(
                                STAGE_ENDS_AT.plusNanos(1)
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
        ContestStage stage = reviewStage(StageStatus.PREPARING);
        stubAdminContestAndStageOrganization();
        given(contestJudgeRepository
                .findByIdAndContestIdForUpdate(JUDGE_ID, CONTEST_ID))
                .willReturn(Optional.of(judge));
        given(contestStageRepository.findByIdForShare(REVIEW_STAGE_ID))
                .willReturn(Optional.of(stage));
        given(reviewRoundEntryRepository
                .findAllForShareByReviewStageIdOrderByIdAsc(
                        REVIEW_STAGE_ID))
                .willReturn(List.of());

        assertCode(
                () -> service.prepareAssignments(
                        ADMIN_ID,
                        CONTEST_PUBLIC_ID,
                        REVIEW_STAGE_ID,
                        JUDGE_ID,
                        new ReviewAssignmentPrepareReq(STAGE_ENDS_AT)
                ),
                ReviewErrorResponseCode.REVIEW_ASSIGNMENT_ENTRY_REQUIRED
        );

        verifyNoInteractions(
                reviewAssignmentRepository,
                adminAuditLogger,
                entityManager
        );
    }

    @Test
    @DisplayName("선정 완료 등 terminal 상태의 엔트리가 포함되면 배정을 거부한다")
    void prepareAssignments_rejectsTerminalEntry() {
        ContestStage stage = reviewStage(StageStatus.PREPARING);
        ReviewRoundEntry entry =
                entry(401L, stage, ReviewRoundEntryStatus.SELECTED);
        stubLockedContext(stage, List.of(entry), List.of());

        assertCode(
                () -> service.prepareAssignments(
                        ADMIN_ID,
                        CONTEST_PUBLIC_ID,
                        REVIEW_STAGE_ID,
                        JUDGE_ID,
                        new ReviewAssignmentPrepareReq(STAGE_ENDS_AT)
                ),
                ReviewErrorResponseCode.REVIEW_ASSIGNMENT_ENTRY_INVALID
        );

        verify(reviewAssignmentRepository, never())
                .saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("요청 대회에 속하지 않는 심사위원은 찾을 수 없는 것으로 처리한다")
    void prepareAssignments_rejectsJudgeOutsideContest() {
        stubAdminContestAndStageOrganization();
        given(contestJudgeRepository
                .findByIdAndContestIdForUpdate(JUDGE_ID, CONTEST_ID))
                .willReturn(Optional.empty());

        assertCode(
                () -> service.prepareAssignments(
                        ADMIN_ID,
                        CONTEST_PUBLIC_ID,
                        REVIEW_STAGE_ID,
                        JUDGE_ID,
                        new ReviewAssignmentPrepareReq(STAGE_ENDS_AT)
                ),
                ReviewErrorResponseCode.CONTEST_JUDGE_NOT_FOUND
        );

        verify(contestStageRepository, never())
                .findByIdForShare(REVIEW_STAGE_ID);
        verifyNoInteractions(
                reviewRoundEntryRepository,
                reviewAssignmentRepository,
                adminAuditLogger,
                entityManager
        );
    }

    @Test
    @DisplayName("요청 대회와 다른 대회의 심사 단계는 찾을 수 없는 것으로 처리한다")
    void prepareAssignments_rejectsStageOutsideContest() {
        Contest otherContest =
                contest(999L, ContestStatus.REVIEWING);
        ContestStage otherStage =
                reviewStage(otherContest, StageStatus.PREPARING);
        stubAdminContestAndStageOrganization();
        given(contestJudgeRepository
                .findByIdAndContestIdForUpdate(JUDGE_ID, CONTEST_ID))
                .willReturn(Optional.of(judge));
        given(contestStageRepository.findByIdForShare(REVIEW_STAGE_ID))
                .willReturn(Optional.of(otherStage));

        assertCode(
                () -> service.prepareAssignments(
                        ADMIN_ID,
                        CONTEST_PUBLIC_ID,
                        REVIEW_STAGE_ID,
                        JUDGE_ID,
                        new ReviewAssignmentPrepareReq(STAGE_ENDS_AT)
                ),
                ContestErrorResponseCode.STAGE_NOT_FOUND
        );

        verifyNoInteractions(
                reviewRoundEntryRepository,
                reviewAssignmentRepository,
                adminAuditLogger,
                entityManager
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
                        REVIEW_STAGE_ID,
                        JUDGE_ID,
                        new ReviewAssignmentPrepareReq(STAGE_ENDS_AT)
                ),
                UserErrorResponseCode.USER_INVALID_TOKEN
        );

        verifyNoInteractions(
                contestRepository,
                contestStageRepository,
                contestJudgeRepository,
                reviewRoundEntryRepository,
                reviewAssignmentRepository,
                adminAuditLogger,
                entityManager
        );
    }

    @Test
    @DisplayName("DB 복합 유니크 제약 충돌은 배정 중복 도메인 오류로 변환한다")
    void prepareAssignments_mapsUniqueViolationToDomainError() {
        ContestStage stage = reviewStage(StageStatus.PREPARING);
        ReviewRoundEntry entry =
                entry(401L, stage, ReviewRoundEntryStatus.ELIGIBLE);
        stubLockedContext(stage, List.of(entry), List.of());
        given(reviewAssignmentRepository.saveAllAndFlush(anyList()))
                .willThrow(new DataIntegrityViolationException("duplicate"));

        assertCode(
                () -> service.prepareAssignments(
                        ADMIN_ID,
                        CONTEST_PUBLIC_ID,
                        REVIEW_STAGE_ID,
                        JUDGE_ID,
                        new ReviewAssignmentPrepareReq(STAGE_ENDS_AT)
                ),
                ReviewErrorResponseCode.REVIEW_ASSIGNMENT_DUPLICATED
        );

        verifyNoInteractions(adminAuditLogger);
        verify(reviewAssignmentRepository, never())
                .findAllWithDetailsByJudgeIdAndReviewStageId(
                        JUDGE_ID,
                        REVIEW_STAGE_ID
                );
    }

    private void stubLockedContext(
            ContestStage stage,
            List<ReviewRoundEntry> entries,
            List<ReviewAssignment> existingAssignments
    ) {
        stubAdminContestAndStageOrganization();
        given(contestJudgeRepository
                .findByIdAndContestIdForUpdate(JUDGE_ID, CONTEST_ID))
                .willReturn(Optional.of(judge));
        given(contestStageRepository.findByIdForShare(REVIEW_STAGE_ID))
                .willReturn(Optional.of(stage));
        given(reviewRoundEntryRepository
                .findAllForShareByReviewStageIdOrderByIdAsc(
                        REVIEW_STAGE_ID))
                .willReturn(entries);
        given(reviewAssignmentRepository
                .findAllForUpdateByJudgeIdAndEntryIdIn(
                        JUDGE_ID,
                        entries.stream()
                                .map(ReviewRoundEntry::getId)
                                .toList()
                )).willReturn(existingAssignments);
    }

    private void stubAdminContestAndStageOrganization() {
        given(userRepository.findById(ADMIN_ID))
                .willReturn(Optional.of(admin));
        given(contestRepository.findByPublicId(CONTEST_PUBLIC_ID))
                .willReturn(Optional.of(contest));
        given(contestStageRepository
                .findOrganizationIdById(REVIEW_STAGE_ID))
                .willReturn(Optional.of(ORGANIZATION_ID));
    }

    private ContestStage reviewStage(StageStatus status) {
        return reviewStage(contest, status);
    }

    private ContestStage reviewStage(
            Contest stageContest,
            StageStatus status
    ) {
        ContestStage stage = ContestStage.builder()
                .contest(stageContest)
                .name("본선 심사")
                .stageType(StageType.REVIEW)
                .sequenceNo(3)
                .status(status)
                .startsAt(NOW.minusHours(1))
                .endsAt(STAGE_ENDS_AT)
                .targetType(StageTargetType.ALL_SUBMISSIONS)
                .passRule(StagePassRule.TOP_N)
                .passCount(1)
                .build();
        ReflectionTestUtils.setField(stage, "id", REVIEW_STAGE_ID);
        return stage;
    }

    private ReviewRoundEntry entry(
            Long id,
            ContestStage stage,
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
                .reviewStage(stage)
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
