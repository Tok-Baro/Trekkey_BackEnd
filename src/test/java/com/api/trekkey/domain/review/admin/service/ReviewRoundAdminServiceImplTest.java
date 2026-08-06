package com.api.trekkey.domain.review.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.api.trekkey.domain.audit.entity.AuditAction;
import com.api.trekkey.domain.audit.support.AdminAuditLogger;
import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStage;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.ParticipationType;
import com.api.trekkey.domain.contest.entity.StageStatus;
import com.api.trekkey.domain.contest.entity.StageType;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.contest.repository.ContestStageRepository;
import com.api.trekkey.domain.credential.integration.WorkCredentialIssuer;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.review.entity.ReviewAssignment;
import com.api.trekkey.domain.review.entity.ReviewAssignmentStatus;
import com.api.trekkey.domain.review.entity.ReviewCriterion;
import com.api.trekkey.domain.review.entity.ReviewRound;
import com.api.trekkey.domain.review.entity.ReviewRoundDecisionRule;
import com.api.trekkey.domain.review.entity.ReviewRoundEntry;
import com.api.trekkey.domain.review.entity.ReviewRoundEntryStatus;
import com.api.trekkey.domain.review.entity.ReviewRoundStatus;
import com.api.trekkey.domain.review.entity.ReviewRoundTargetType;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.repository.ReviewAssignmentRepository;
import com.api.trekkey.domain.review.repository.ReviewCriterionRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundEntryRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundRepository;
import com.api.trekkey.domain.review.admin.web.dto.request.ReviewRoundDeadlineExtendReq;
import com.api.trekkey.domain.review.admin.web.dto.request.ReviewRoundCriterionReq;
import com.api.trekkey.domain.review.admin.web.dto.request.ReviewRoundSaveReq;
import com.api.trekkey.domain.review.admin.web.dto.response.ReviewRoundRes;
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
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReviewRoundAdminServiceImplTest {

    private static final Long ADMIN_ID = 10L;
    private static final Long ORGANIZATION_ID = 20L;
    private static final Long CONTEST_ID = 30L;
    private static final Long ROUND_ID = 40L;
    private static final String CONTEST_PUBLIC_ID = "contest-public-id";
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 7, 27, 12, 0);
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime UTC_NOW =
            LocalDateTime.of(2026, 7, 27, 3, 0);
    private static final LocalDateTime STARTS_AT =
            LocalDateTime.of(2026, 8, 1, 9, 0);
    private static final LocalDateTime ENDS_AT =
            LocalDateTime.of(2026, 8, 2, 18, 0);

    @Mock
    private UserRepository userRepository;

    @Mock
    private ContestRepository contestRepository;

    @Mock
    private ContestStageRepository contestStageRepository;

    @Mock
    private ReviewRoundRepository reviewRoundRepository;

    @Mock
    private ReviewCriterionRepository reviewCriterionRepository;

    @Mock
    private ReviewRoundEntryRepository reviewRoundEntryRepository;

    @Mock
    private ReviewAssignmentRepository reviewAssignmentRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private WorkCredentialIssuer workCredentialIssuer;

    @Mock
    private AdminAuditLogger adminAuditLogger;

    @Mock
    private EntityManager entityManager;

    private ReviewRoundAdminServiceImpl service;
    private Organization organization;
    private User admin;
    private Contest contest;

    @BeforeEach
    void setUp() {
        service = new ReviewRoundAdminServiceImpl(
                userRepository,
                contestRepository,
                contestStageRepository,
                reviewRoundRepository,
                reviewCriterionRepository,
                reviewRoundEntryRepository,
                reviewAssignmentRepository,
                teamRepository,
                submissionRepository,
                workCredentialIssuer,
                adminAuditLogger,
                entityManager,
                Clock.fixed(NOW.atZone(ZONE_ID).toInstant(), ZONE_ID)
        );

        organization = org.mockito.Mockito.mock(Organization.class);
        org.mockito.Mockito.lenient()
                .when(organization.getId())
                .thenReturn(ORGANIZATION_ID);
        admin = User.builder()
                .organization(organization)
                .name("관리자")
                .email("admin@example.com")
                .password("encoded")
                .role(UserRole.ADMIN)
                .memberType(MemberType.STAFF)
                .status(UserStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(admin, "id", ADMIN_ID);
        contest = Contest.builder()
                .publicId(CONTEST_PUBLIC_ID)
                .organization(organization)
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
        ReflectionTestUtils.setField(contest, "id", CONTEST_ID);

        given(userRepository.findById(ADMIN_ID))
                .willReturn(Optional.of(admin));
        given(contestRepository.findByPublicId(CONTEST_PUBLIC_ID))
                .willReturn(Optional.of(contest));
    }

    @Test
    @DisplayName("심사 라운드와 라운드별 평가 기준을 함께 생성한다")
    void createRound_createsRoundAndCriteria() {
        given(reviewRoundRepository
                .findAllForUpdateByContestIdOrderByRoundNoAsc(CONTEST_ID))
                .willReturn(List.of());
        given(reviewRoundRepository.saveAndFlush(any(ReviewRound.class)))
                .willAnswer(invocation -> {
                    ReviewRound round = invocation.getArgument(0);
                    ReflectionTestUtils.setField(round, "id", ROUND_ID);
                    return round;
                });

        ReviewRoundRes response = service.createRound(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                request()
        );

        assertThat(response.id()).isEqualTo(ROUND_ID);
        assertThat(response.status()).isEqualTo(ReviewRoundStatus.PREPARING);
        assertThat(response.roundNo()).isEqualTo(1);
        assertThat(response.criteria()).singleElement().satisfies(criterion -> {
            assertThat(criterion.code()).isEqualTo("creativity");
            assertThat(criterion.maxScore()).isEqualTo(50);
        });

        verify(entityManager).refresh(
                contest,
                LockModeType.PESSIMISTIC_WRITE
        );
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReviewCriterion>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(reviewCriterionRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(criterion -> {
            assertThat(criterion.getReviewRound().getId()).isEqualTo(ROUND_ID);
            assertThat(criterion.getCode()).isEqualTo("creativity");
            assertThat(criterion.isActive()).isTrue();
        });
        verify(adminAuditLogger).log(
                ADMIN_ID,
                ORGANIZATION_ID,
                AuditAction.REVIEW_ROUND_CREATE,
                "REVIEW_ROUND",
                ROUND_ID,
                "contestId=30, roundNo=1"
        );
    }

    @Test
    @DisplayName("첫 심사 라운드는 제출 마감보다 먼저 시작하도록 설정할 수 없다")
    void createRound_rejectsStartBeforeSubmissionDeadline() {
        ContestStage submissionStage = ContestStage.builder()
                .contest(contest)
                .name("작품 제출")
                .stageType(StageType.SUBMISSION)
                .sequenceNo(1)
                .status(StageStatus.OPEN)
                .endsAt(STARTS_AT.plusHours(1))
                .build();
        given(reviewRoundRepository
                .findAllForUpdateByContestIdOrderByRoundNoAsc(CONTEST_ID))
                .willReturn(List.of());
        given(contestStageRepository
                .findAllForShareByContestIdAndStageTypeOrderBySequenceNoAsc(
                        CONTEST_ID,
                        StageType.SUBMISSION))
                .willReturn(List.of(submissionStage));

        assertThatThrownBy(() -> service.createRound(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                request()
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception ->
                        ((CustomException) exception)
                                .getBaseResponseCode())
                .isEqualTo(
                        ReviewErrorResponseCode
                                .REVIEW_ROUND_SUBMISSION_WINDOW_INVALID);

        verify(reviewRoundRepository,
                org.mockito.Mockito.never())
                .saveAndFlush(any(ReviewRound.class));
    }

    @Test
    @DisplayName("수동 선정 방식으로 첫 라운드를 생성할 수 있다")
    void createRound_supportsManualTargetType() {
        ReviewRoundTargetType targetType =
                ReviewRoundTargetType.MANUAL;
        ReviewRoundSaveReq req = new ReviewRoundSaveReq(
                1,
                "예선 심사",
                STARTS_AT,
                ENDS_AT,
                targetType,
                ReviewRoundDecisionRule.TOP_N,
                10,
                null,
                List.of(new ReviewRoundCriterionReq(
                        null,
                        "creativity",
                        "창의성",
                        50,
                        1
                ))
        );
        given(reviewRoundRepository
                .findAllForUpdateByContestIdOrderByRoundNoAsc(CONTEST_ID))
                .willReturn(List.of());
        given(reviewRoundRepository.saveAndFlush(any(ReviewRound.class)))
                .willAnswer(invocation -> {
                    ReviewRound round = invocation.getArgument(0);
                    ReflectionTestUtils.setField(round, "id", ROUND_ID);
                    return round;
                });

        ReviewRoundRes response = service.createRound(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                req
        );

        assertThat(response.targetType()).isEqualTo(targetType);
    }

    @Test
    @DisplayName("심사 없는 수동 라운드는 평가 기준 없이 생성할 수 있다")
    void createRound_supportsManualDecisionWithoutCriteria() {
        ReviewRoundSaveReq req = new ReviewRoundSaveReq(
                1,
                "수동 선정",
                STARTS_AT,
                ENDS_AT,
                ReviewRoundTargetType.MANUAL,
                ReviewRoundDecisionRule.MANUAL,
                null,
                null,
                List.of()
        );
        given(reviewRoundRepository
                .findAllForUpdateByContestIdOrderByRoundNoAsc(CONTEST_ID))
                .willReturn(List.of());
        given(reviewRoundRepository.saveAndFlush(any(ReviewRound.class)))
                .willAnswer(invocation -> {
                    ReviewRound round = invocation.getArgument(0);
                    ReflectionTestUtils.setField(round, "id", ROUND_ID);
                    return round;
                });

        ReviewRoundRes response = service.createRound(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                req
        );

        assertThat(response.targetType())
                .isEqualTo(ReviewRoundTargetType.MANUAL);
        assertThat(response.decisionRule())
                .isEqualTo(ReviewRoundDecisionRule.MANUAL);
        assertThat(response.criteria()).isEmpty();
    }

    @Test
    @DisplayName("첫 라운드는 이전 라운드 선정작을 대상으로 설정할 수 없다")
    void createRound_rejectsPreviousSelectedForFirstRound() {
        assertThatThrownBy(() -> service.createRound(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                request(ReviewRoundTargetType.PREVIOUS_SELECTED)
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception ->
                        ((CustomException) exception)
                                .getBaseResponseCode())
                .isEqualTo(
                        ReviewErrorResponseCode
                                .REVIEW_ROUND_CONFIGURATION_INVALID);

        verifyNoInteractions(reviewRoundRepository);
    }

    @Test
    @DisplayName("첫 라운드는 반드시 1번으로 생성해야 한다")
    void createRound_rejectsSequenceGap() {
        ReviewRoundSaveReq req = new ReviewRoundSaveReq(
                2,
                "예선 심사",
                STARTS_AT,
                ENDS_AT,
                ReviewRoundTargetType.ALL_SUBMISSIONS,
                ReviewRoundDecisionRule.TOP_N,
                10,
                null,
                List.of(new ReviewRoundCriterionReq(
                        null,
                        "creativity",
                        "창의성",
                        50,
                        1
                ))
        );
        given(reviewRoundRepository
                .findAllForUpdateByContestIdOrderByRoundNoAsc(CONTEST_ID))
                .willReturn(List.of());

        assertThatThrownBy(() -> service.createRound(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                req
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception ->
                        ((CustomException) exception)
                                .getBaseResponseCode())
                .isEqualTo(
                        ReviewErrorResponseCode
                                .REVIEW_ROUND_SEQUENCE_INVALID);
    }

    @Test
    @DisplayName("수상이 확정된 대회에는 새 심사 라운드를 생성할 수 없다")
    void createRound_rejectsAwardedContest() {
        ReflectionTestUtils.setField(contest, "status", ContestStatus.AWARDED);

        assertThatThrownBy(() -> service.createRound(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                request()
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception ->
                        ((CustomException) exception)
                                .getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode
                        .REVIEW_ROUND_CONFIGURATION_LOCKED);

        verify(entityManager).refresh(
                contest,
                LockModeType.PESSIMISTIC_WRITE);
        verifyNoInteractions(reviewRoundRepository);
    }

    @Test
    @DisplayName("준비 중인 첫 라운드는 수동 대상 선정 방식으로 변경할 수 있다")
    void updateRound_supportsManualTargetType() {
        ReviewRoundTargetType targetType =
                ReviewRoundTargetType.MANUAL;
        ReviewRound round = round(ReviewRoundStatus.PREPARING);
        ReviewCriterion criterion = criterion(round);
        stubRoundOrganization();
        given(reviewRoundRepository
                .findAllForUpdateByContestIdOrderByRoundNoAsc(CONTEST_ID))
                .willReturn(List.of(round));
        given(reviewCriterionRepository
                .findAllForUpdateByReviewRoundIdInOrderBySortOrderAsc(
                        List.of(ROUND_ID)))
                .willReturn(List.of(criterion));
        given(reviewRoundEntryRepository
                .findAllForShareByReviewRoundIdOrderByIdAsc(ROUND_ID))
                .willReturn(List.of());

        ReviewRoundRes response = service.updateRound(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                ROUND_ID,
                request(targetType)
        );

        assertThat(response.targetType()).isEqualTo(targetType);
        assertThat(round.getTargetType()).isEqualTo(targetType);
    }

    @Test
    @DisplayName("개별 라운드 수정으로 라운드 순서를 바꿀 수 없다")
    void updateRound_rejectsRoundNumberChange() {
        ReviewRound round = round(ReviewRoundStatus.PREPARING);
        stubRoundOrganization();
        given(reviewRoundRepository
                .findAllForUpdateByContestIdOrderByRoundNoAsc(CONTEST_ID))
                .willReturn(List.of(round));
        ReviewRoundSaveReq req = new ReviewRoundSaveReq(
                2,
                "예선 심사",
                STARTS_AT,
                ENDS_AT,
                ReviewRoundTargetType.ALL_SUBMISSIONS,
                ReviewRoundDecisionRule.TOP_N,
                10,
                null,
                List.of(new ReviewRoundCriterionReq(
                        null,
                        "creativity",
                        "창의성",
                        50,
                        1
                ))
        );

        assertThatThrownBy(() -> service.updateRound(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                ROUND_ID,
                req
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception ->
                        ((CustomException) exception)
                                .getBaseResponseCode())
                .isEqualTo(
                        ReviewErrorResponseCode
                                .REVIEW_ROUND_SEQUENCE_INVALID);

        verifyNoInteractions(reviewCriterionRepository);
    }

    @Test
    @DisplayName("수상이 확정된 대회의 심사 라운드는 수정할 수 없다")
    void updateRound_rejectsAwardedContest() {
        ReflectionTestUtils.setField(contest, "status", ContestStatus.AWARDED);

        assertThatThrownBy(() -> service.updateRound(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                ROUND_ID,
                request()
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception ->
                        ((CustomException) exception)
                                .getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode
                        .REVIEW_ROUND_CONFIGURATION_LOCKED);

        verify(entityManager).refresh(
                contest,
                LockModeType.PESSIMISTIC_WRITE);
        verifyNoInteractions(reviewRoundRepository);
    }

    @Test
    @DisplayName("진행 중인 심사 라운드의 설정은 변경할 수 없다")
    void updateRound_rejectsOpenRound() {
        ReviewRound round = round(ReviewRoundStatus.OPEN);
        stubRoundOrganization();
        given(reviewRoundRepository
                .findAllForUpdateByContestIdOrderByRoundNoAsc(CONTEST_ID))
                .willReturn(List.of(round));

        assertThatThrownBy(() -> service.updateRound(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                ROUND_ID,
                request()
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception ->
                        ((CustomException) exception)
                                .getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode
                        .REVIEW_ROUND_CONFIGURATION_LOCKED);

        verifyNoInteractions(reviewCriterionRepository);
    }

    @Test
    @DisplayName("심사 대상이 준비된 라운드의 설정은 변경할 수 없다")
    void updateRound_rejectsRoundWithEntries() {
        ReviewRound round = round(ReviewRoundStatus.PREPARING);
        ReviewCriterion criterion = criterion(round);
        stubRoundOrganization();
        given(reviewRoundRepository
                .findAllForUpdateByContestIdOrderByRoundNoAsc(CONTEST_ID))
                .willReturn(List.of(round));
        given(reviewCriterionRepository
                .findAllForUpdateByReviewRoundIdInOrderBySortOrderAsc(
                        List.of(ROUND_ID)))
                .willReturn(List.of(criterion));
        given(reviewRoundEntryRepository
                .findAllForShareByReviewRoundIdOrderByIdAsc(ROUND_ID))
                .willReturn(List.of(entry(round, 101L)));

        assertThatThrownBy(() -> service.updateRound(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                ROUND_ID,
                request()
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception ->
                        ((CustomException) exception)
                                .getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode
                        .REVIEW_ROUND_CONFIGURATION_LOCKED);
    }

    @Test
    @DisplayName("종료 시각이 지난 OPEN 라운드도 미래 시각으로 연장해 다시 진행할 수 있다")
    void extendDeadline_extendsExpiredOpenRound() {
        ReviewRound round = round(ReviewRoundStatus.OPEN);
        LocalDateTime expiredEndsAt = NOW.minusMinutes(1);
        LocalDateTime extendedEndsAt = NOW.plusDays(1);
        ReflectionTestUtils.setField(round, "startsAt", NOW.minusDays(1));
        ReflectionTestUtils.setField(round, "endsAt", expiredEndsAt);
        ReviewCriterion criterion = criterion(round);
        stubRoundOrganization();
        given(reviewRoundRepository.findByIdForUpdate(ROUND_ID))
                .willReturn(Optional.of(round));
        given(reviewCriterionRepository
                .findAllByReviewRoundIdInOrderBySortOrderAsc(
                        List.of(ROUND_ID)))
                .willReturn(List.of(criterion));

        ReviewRoundRes response = service.extendDeadline(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                ROUND_ID,
                new ReviewRoundDeadlineExtendReq(extendedEndsAt)
        );

        assertThat(response.endsAt()).isEqualTo(extendedEndsAt);
        assertThat(round.isOpenAt(NOW)).isTrue();
        verify(reviewRoundRepository).flush();
        verify(adminAuditLogger).log(
                ADMIN_ID,
                ORGANIZATION_ID,
                AuditAction.REVIEW_ROUND_DEADLINE_EXTEND,
                "REVIEW_ROUND",
                ROUND_ID,
                "endsAt=" + expiredEndsAt + "->" + extendedEndsAt
        );
    }

    @Test
    @DisplayName("OPEN 라운드의 종료 시각은 현재와 기존 종료 시각보다 뒤로만 연장할 수 있다")
    void extendDeadline_rejectsNonExtension() {
        ReviewRound round = round(ReviewRoundStatus.OPEN);
        stubRoundOrganization();
        given(reviewRoundRepository.findByIdForUpdate(ROUND_ID))
                .willReturn(Optional.of(round));

        assertThatThrownBy(() -> service.extendDeadline(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                ROUND_ID,
                new ReviewRoundDeadlineExtendReq(
                        ENDS_AT.minusMinutes(1))
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception ->
                        ((CustomException) exception)
                                .getBaseResponseCode())
                .isEqualTo(
                        ReviewErrorResponseCode
                                .REVIEW_ROUND_DEADLINE_INVALID);

        assertThat(round.getEndsAt()).isEqualTo(ENDS_AT);
        verify(reviewRoundRepository,
                org.mockito.Mockito.never()).flush();
    }

    @Test
    @DisplayName("준비 중이거나 확정된 라운드는 종료 시각 연장 API로 수정할 수 없다")
    void extendDeadline_rejectsNonOpenRound() {
        ReviewRound round = round(ReviewRoundStatus.PREPARING);
        stubRoundOrganization();
        given(reviewRoundRepository.findByIdForUpdate(ROUND_ID))
                .willReturn(Optional.of(round));

        assertThatThrownBy(() -> service.extendDeadline(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                ROUND_ID,
                new ReviewRoundDeadlineExtendReq(
                        ENDS_AT.plusDays(1))
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception ->
                        ((CustomException) exception)
                                .getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode
                        .REVIEW_ROUND_STATUS_TRANSITION_INVALID);
    }

    @Test
    @DisplayName("라운드를 열면 모든 ELIGIBLE 심사 대상을 IN_REVIEW로 전환한다")
    void openRound_opensRoundAndStartsEntries() {
        ReflectionTestUtils.setField(
                contest,
                "status",
                ContestStatus.APPLICATION_OPEN);
        ReviewRound round = round(ReviewRoundStatus.PREPARING);
        ReviewCriterion criterion = criterion(round);
        Team team = approvedTeam();
        Submission firstSubmission =
                submittedSubmission(team, 201L, "submission-1");
        Submission secondSubmission =
                submittedSubmission(team, 202L, "submission-2");
        ReviewRoundEntry first = entry(round, firstSubmission, 101L);
        ReviewRoundEntry second = entry(round, secondSubmission, 102L);
        stubRoundOrganization();
        given(reviewRoundRepository.findByIdForUpdate(ROUND_ID))
                .willReturn(Optional.of(round));
        given(reviewCriterionRepository
                .findAllForShareByReviewRoundIdOrderBySortOrderAsc(ROUND_ID))
                .willReturn(List.of(criterion));
        given(reviewRoundEntryRepository
                .findAllForUpdateByReviewRoundIdOrderByIdAsc(ROUND_ID))
                .willReturn(List.of(first, second));
        given(reviewAssignmentRepository
                .findAllForShareByReviewRoundEntryIdInOrderByEntryIdAscIdAsc(
                        List.of(101L, 102L)))
                .willReturn(List.of(
                        assignment(first, 301L,
                                ReviewAssignmentStatus.ASSIGNED),
                        assignment(second, 302L,
                                ReviewAssignmentStatus.COMPLETED)
                ));
        given(teamRepository
                .findAllForUpdateByContestIdOrderByIdAsc(CONTEST_ID))
                .willReturn(List.of(team));
        given(submissionRepository
                .findAllForUpdateByContestIdAndStatusAndTeamStatus(
                        CONTEST_ID,
                        SubmissionStatus.SUBMITTED,
                        TeamStatus.APPROVED
                ))
                .willReturn(List.of(firstSubmission, secondSubmission));

        ReviewRoundRes response = service.openRound(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                ROUND_ID
        );

        assertThat(response.status()).isEqualTo(ReviewRoundStatus.OPEN);
        assertThat(contest.getStatus()).isEqualTo(ContestStatus.REVIEWING);
        assertThat(first.getStatus())
                .isEqualTo(ReviewRoundEntryStatus.IN_REVIEW);
        assertThat(second.getStatus())
                .isEqualTo(ReviewRoundEntryStatus.IN_REVIEW);
        assertThat(firstSubmission.getFinalizedAt()).isEqualTo(UTC_NOW);
        assertThat(secondSubmission.getFinalizedAt()).isEqualTo(UTC_NOW);
        verify(workCredentialIssuer)
                .issueForFinalizedSubmission(firstSubmission);
        verify(workCredentialIssuer)
                .issueForFinalizedSubmission(secondSubmission);

        InOrder lockOrder = inOrder(
                reviewRoundRepository,
                reviewCriterionRepository,
                reviewRoundEntryRepository,
                reviewAssignmentRepository,
                teamRepository,
                submissionRepository
        );
        lockOrder.verify(reviewRoundRepository).findByIdForUpdate(ROUND_ID);
        lockOrder.verify(reviewCriterionRepository)
                .findAllForShareByReviewRoundIdOrderBySortOrderAsc(ROUND_ID);
        lockOrder.verify(reviewRoundEntryRepository)
                .findAllForUpdateByReviewRoundIdOrderByIdAsc(ROUND_ID);
        lockOrder.verify(reviewAssignmentRepository)
                .findAllForShareByReviewRoundEntryIdInOrderByEntryIdAscIdAsc(
                        List.of(101L, 102L));
        lockOrder.verify(teamRepository)
                .findAllForUpdateByContestIdOrderByIdAsc(CONTEST_ID);
        lockOrder.verify(submissionRepository)
                .findAllForUpdateByContestIdAndStatusAndTeamStatus(
                        CONTEST_ID,
                        SubmissionStatus.SUBMITTED,
                        TeamStatus.APPROVED
                );
        verify(reviewRoundRepository).flush();
        verify(reviewRoundEntryRepository).flush();
        verify(adminAuditLogger).log(
                ADMIN_ID,
                ORGANIZATION_ID,
                AuditAction.REVIEW_ROUND_OPEN,
                "REVIEW_ROUND",
                ROUND_ID,
                "contestId=30, entryCount=2"
        );
    }

    @Test
    @DisplayName("수동 대상·수동 판정 라운드는 평가 기준과 심사 배정 없이 시작한다")
    void openRound_supportsManualRoundWithoutReviews() {
        ReviewRound round = round(ReviewRoundStatus.PREPARING);
        ReflectionTestUtils.setField(
                round,
                "targetType",
                ReviewRoundTargetType.MANUAL);
        ReflectionTestUtils.setField(
                round,
                "decisionRule",
                ReviewRoundDecisionRule.MANUAL);
        ReflectionTestUtils.setField(round, "selectCount", null);
        Team team = approvedTeam();
        Submission submission =
                submittedSubmission(team, 201L, "submission-1");
        ReviewRoundEntry entry = entry(round, submission, 101L);
        stubRoundOrganization();
        given(reviewRoundRepository.findByIdForUpdate(ROUND_ID))
                .willReturn(Optional.of(round));
        given(reviewCriterionRepository
                .findAllForShareByReviewRoundIdOrderBySortOrderAsc(ROUND_ID))
                .willReturn(List.of());
        given(reviewRoundEntryRepository
                .findAllForUpdateByReviewRoundIdOrderByIdAsc(ROUND_ID))
                .willReturn(List.of(entry));
        given(teamRepository
                .findAllForUpdateByContestIdOrderByIdAsc(CONTEST_ID))
                .willReturn(List.of(team));
        given(submissionRepository
                .findAllForUpdateByContestIdAndStatusAndTeamStatus(
                        CONTEST_ID,
                        SubmissionStatus.SUBMITTED,
                        TeamStatus.APPROVED))
                .willReturn(List.of(submission));

        ReviewRoundRes response = service.openRound(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                ROUND_ID
        );

        assertThat(response.status()).isEqualTo(ReviewRoundStatus.OPEN);
        assertThat(response.criteria()).isEmpty();
        assertThat(entry.getStatus())
                .isEqualTo(ReviewRoundEntryStatus.IN_REVIEW);
        assertThat(submission.getFinalizedAt()).isEqualTo(UTC_NOW);
        verify(workCredentialIssuer)
                .issueForFinalizedSubmission(submission);
        verifyNoInteractions(reviewAssignmentRepository);
    }

    @Test
    @DisplayName("앞선 라운드가 확정되지 않으면 다음 라운드를 시작할 수 없다")
    void openRound_rejectsWhenPreviousRoundIsNotFinalized() {
        ReviewRound previous = round(ReviewRoundStatus.OPEN);
        ReflectionTestUtils.setField(previous, "id", 39L);
        ReviewRound current = round(ReviewRoundStatus.PREPARING);
        ReflectionTestUtils.setField(current, "roundNo", 2);
        stubRoundOrganization();
        given(reviewRoundRepository.findByIdForUpdate(ROUND_ID))
                .willReturn(Optional.of(current));
        given(reviewRoundRepository
                .findAllForUpdateByContestIdOrderByRoundNoAsc(CONTEST_ID))
                .willReturn(List.of(previous, current));

        assertThatThrownBy(() -> service.openRound(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                ROUND_ID
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception ->
                        ((CustomException) exception)
                                .getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode
                        .REVIEW_ROUND_PREVIOUS_NOT_FINALIZED);

        verifyNoInteractions(reviewCriterionRepository);
    }

    @Test
    @DisplayName("다른 라운드가 진행 중이면 새 라운드를 동시에 시작할 수 없다")
    void openRound_rejectsWhenAnotherRoundIsOpen() {
        ReviewRound current = round(ReviewRoundStatus.PREPARING);
        ReviewRound later = round(ReviewRoundStatus.OPEN);
        ReflectionTestUtils.setField(later, "id", 41L);
        ReflectionTestUtils.setField(later, "roundNo", 2);
        stubRoundOrganization();
        given(reviewRoundRepository.findByIdForUpdate(ROUND_ID))
                .willReturn(Optional.of(current));
        given(reviewRoundRepository
                .findAllForUpdateByContestIdOrderByRoundNoAsc(CONTEST_ID))
                .willReturn(List.of(current, later));

        assertThatThrownBy(() -> service.openRound(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                ROUND_ID
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception ->
                        ((CustomException) exception)
                                .getBaseResponseCode())
                .isEqualTo(
                        ReviewErrorResponseCode.REVIEW_ROUND_ALREADY_OPEN);
    }

    @Test
    @DisplayName("참가 명단을 확정하지 않은 팀의 제출물로는 라운드를 시작할 수 없다")
    void openRound_rejectsTeamWithUnfinalizedParticipation() {
        ReviewRound round = round(ReviewRoundStatus.PREPARING);
        ReviewCriterion criterion = criterion(round);
        Team team = approvedTeam();
        ReflectionTestUtils.setField(
                team,
                "participationFinalizedAt",
                null);
        Submission submission =
                submittedSubmission(team, 201L, "submission-1");
        ReviewRoundEntry entry = entry(round, submission, 101L);
        stubRoundOrganization();
        given(reviewRoundRepository.findByIdForUpdate(ROUND_ID))
                .willReturn(Optional.of(round));
        given(reviewRoundRepository
                .findAllForUpdateByContestIdOrderByRoundNoAsc(CONTEST_ID))
                .willReturn(List.of(round));
        given(reviewCriterionRepository
                .findAllForShareByReviewRoundIdOrderBySortOrderAsc(ROUND_ID))
                .willReturn(List.of(criterion));
        given(reviewRoundEntryRepository
                .findAllForUpdateByReviewRoundIdOrderByIdAsc(ROUND_ID))
                .willReturn(List.of(entry));
        given(reviewAssignmentRepository
                .findAllForShareByReviewRoundEntryIdInOrderByEntryIdAscIdAsc(
                        List.of(101L)))
                .willReturn(List.of(assignment(
                        entry,
                        301L,
                        ReviewAssignmentStatus.ASSIGNED)));
        given(teamRepository
                .findAllForUpdateByContestIdOrderByIdAsc(CONTEST_ID))
                .willReturn(List.of(team));

        assertThatThrownBy(() -> service.openRound(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                ROUND_ID
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception ->
                        ((CustomException) exception)
                                .getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode
                        .REVIEW_ENTRY_TEAM_NOT_FINALIZED);

        verifyNoInteractions(submissionRepository);
        verifyNoInteractions(workCredentialIssuer);
    }

    @Test
    @DisplayName("전체 제출 대상이 준비 후 변경되면 동기화 전에는 라운드를 열 수 없다")
    void openRound_rejectsChangedAllSubmissionSnapshot() {
        ReviewRound round = round(ReviewRoundStatus.PREPARING);
        ReviewCriterion criterion = criterion(round);
        Team team = approvedTeam();
        Submission preparedSubmission =
                submittedSubmission(team, 201L, "submission-1");
        Submission lateSubmission =
                submittedSubmission(team, 202L, "submission-2");
        ReviewRoundEntry entry =
                entry(round, preparedSubmission, 101L);
        stubRoundOrganization();
        given(reviewRoundRepository.findByIdForUpdate(ROUND_ID))
                .willReturn(Optional.of(round));
        given(reviewCriterionRepository
                .findAllForShareByReviewRoundIdOrderBySortOrderAsc(ROUND_ID))
                .willReturn(List.of(criterion));
        given(reviewRoundEntryRepository
                .findAllForUpdateByReviewRoundIdOrderByIdAsc(ROUND_ID))
                .willReturn(List.of(entry));
        given(reviewAssignmentRepository
                .findAllForShareByReviewRoundEntryIdInOrderByEntryIdAscIdAsc(
                        List.of(101L)))
                .willReturn(List.of(assignment(
                        entry,
                        301L,
                        ReviewAssignmentStatus.ASSIGNED)));
        given(teamRepository
                .findAllForUpdateByContestIdOrderByIdAsc(CONTEST_ID))
                .willReturn(List.of(team));
        given(submissionRepository
                .findAllForUpdateByContestIdAndStatusAndTeamStatus(
                        CONTEST_ID,
                        SubmissionStatus.SUBMITTED,
                        TeamStatus.APPROVED))
                .willReturn(List.of(
                        preparedSubmission,
                        lateSubmission));

        assertThatThrownBy(() -> service.openRound(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                ROUND_ID
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception ->
                        ((CustomException) exception)
                                .getBaseResponseCode())
                .isEqualTo(
                        ReviewErrorResponseCode
                                .REVIEW_ENTRY_SYNC_REQUIRED);

        assertThat(round.getStatus())
                .isEqualTo(ReviewRoundStatus.PREPARING);
        assertThat(entry.getStatus())
                .isEqualTo(ReviewRoundEntryStatus.ELIGIBLE);
        assertThat(preparedSubmission.getFinalizedAt()).isNull();
        assertThat(lateSubmission.getFinalizedAt()).isNull();
    }

    @Test
    @DisplayName("제출 마감 시각이 지나기 전에는 첫 심사 라운드를 열 수 없다")
    void openRound_rejectsBeforeSubmissionDeadline() {
        ReviewRound round = round(ReviewRoundStatus.PREPARING);
        ContestStage submissionStage = ContestStage.builder()
                .contest(contest)
                .name("작품 제출")
                .stageType(StageType.SUBMISSION)
                .sequenceNo(1)
                .status(StageStatus.OPEN)
                .endsAt(NOW.plusMinutes(1))
                .build();
        stubRoundOrganization();
        given(reviewRoundRepository.findByIdForUpdate(ROUND_ID))
                .willReturn(Optional.of(round));
        given(contestStageRepository
                .findAllForShareByContestIdAndStageTypeOrderBySequenceNoAsc(
                        CONTEST_ID,
                        StageType.SUBMISSION))
                .willReturn(List.of(submissionStage));

        assertThatThrownBy(() -> service.openRound(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                ROUND_ID
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception ->
                        ((CustomException) exception)
                                .getBaseResponseCode())
                .isEqualTo(
                        ReviewErrorResponseCode
                                .REVIEW_ROUND_SUBMISSION_WINDOW_INVALID);

        verifyNoInteractions(
                reviewCriterionRepository,
                reviewRoundEntryRepository);
    }

    @Test
    @DisplayName("수상이 확정된 대회는 심사 라운드를 다시 열 수 없다")
    void openRound_rejectsAwardedContest() {
        ReflectionTestUtils.setField(contest, "status", ContestStatus.AWARDED);

        assertThatThrownBy(() -> service.openRound(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                ROUND_ID
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception ->
                        ((CustomException) exception)
                                .getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode
                        .REVIEW_ROUND_STATUS_TRANSITION_INVALID);

        verify(entityManager).refresh(
                contest,
                LockModeType.PESSIMISTIC_WRITE);
        verifyNoInteractions(reviewRoundRepository);
    }

    @Test
    @DisplayName("심사위원이 배정되지 않은 대상이 있으면 라운드를 열 수 없다")
    void openRound_rejectsEntryWithoutActiveAssignment() {
        ReviewRound round = round(ReviewRoundStatus.PREPARING);
        ReviewCriterion criterion = criterion(round);
        Team team = approvedTeam();
        Submission firstSubmission =
                submittedSubmission(team, 201L, "submission-1");
        Submission secondSubmission =
                submittedSubmission(team, 202L, "submission-2");
        ReviewRoundEntry first = entry(round, firstSubmission, 101L);
        ReviewRoundEntry second = entry(round, secondSubmission, 102L);
        stubRoundOrganization();
        given(reviewRoundRepository.findByIdForUpdate(ROUND_ID))
                .willReturn(Optional.of(round));
        given(reviewCriterionRepository
                .findAllForShareByReviewRoundIdOrderBySortOrderAsc(ROUND_ID))
                .willReturn(List.of(criterion));
        given(reviewRoundEntryRepository
                .findAllForUpdateByReviewRoundIdOrderByIdAsc(ROUND_ID))
                .willReturn(List.of(first, second));
        given(reviewAssignmentRepository
                .findAllForShareByReviewRoundEntryIdInOrderByEntryIdAscIdAsc(
                        List.of(101L, 102L)))
                .willReturn(List.of(
                        assignment(first, 301L,
                                ReviewAssignmentStatus.ASSIGNED),
                        assignment(second, 302L,
                                ReviewAssignmentStatus.CANCELED)
                ));

        assertThatThrownBy(() -> service.openRound(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                ROUND_ID
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception ->
                        ((CustomException) exception)
                                .getBaseResponseCode())
                .isEqualTo(
                        ReviewErrorResponseCode.REVIEW_ASSIGNMENT_REQUIRED);

        assertThat(round.getStatus()).isEqualTo(ReviewRoundStatus.PREPARING);
        assertThat(first.getStatus())
                .isEqualTo(ReviewRoundEntryStatus.ELIGIBLE);
        assertThat(second.getStatus())
                .isEqualTo(ReviewRoundEntryStatus.ELIGIBLE);
        assertThat(firstSubmission.getFinalizedAt()).isNull();
        assertThat(secondSubmission.getFinalizedAt()).isNull();
        verifyNoInteractions(teamRepository, submissionRepository);
    }

    @Test
    @DisplayName("활성 평가 기준 없이 심사 라운드를 열 수 없다")
    void openRound_rejectsMissingCriteria() {
        ReviewRound round = round(ReviewRoundStatus.PREPARING);
        stubRoundOrganization();
        given(reviewRoundRepository.findByIdForUpdate(ROUND_ID))
                .willReturn(Optional.of(round));
        given(reviewCriterionRepository
                .findAllForShareByReviewRoundIdOrderBySortOrderAsc(ROUND_ID))
                .willReturn(List.of());

        assertThatThrownBy(() -> service.openRound(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                ROUND_ID
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception ->
                        ((CustomException) exception)
                                .getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode
                        .REVIEW_ROUND_CRITERION_REQUIRED);

        verifyNoInteractions(reviewRoundEntryRepository);
    }

    @Test
    @DisplayName("FINALIZED 라운드는 오픈 API로 다시 전환할 수 없다")
    void openRound_rejectsFinalizedRound() {
        ReviewRound round = round(ReviewRoundStatus.FINALIZED);
        stubRoundOrganization();
        given(reviewRoundRepository.findByIdForUpdate(ROUND_ID))
                .willReturn(Optional.of(round));

        assertThatThrownBy(() -> service.openRound(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                ROUND_ID
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception ->
                        ((CustomException) exception)
                                .getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode
                        .REVIEW_ROUND_STATUS_TRANSITION_INVALID);

        verifyNoInteractions(
                reviewCriterionRepository,
                reviewRoundEntryRepository);
    }

    @Test
    @DisplayName("종료 시각이 지난 심사 라운드는 열 수 없다")
    void openRound_rejectsExpiredRound() {
        ReviewRound round = round(ReviewRoundStatus.PREPARING);
        ReflectionTestUtils.setField(
                round,
                "startsAt",
                NOW.minusHours(2)
        );
        ReflectionTestUtils.setField(
                round,
                "endsAt",
                NOW.minusMinutes(1)
        );
        stubRoundOrganization();
        given(reviewRoundRepository.findByIdForUpdate(ROUND_ID))
                .willReturn(Optional.of(round));

        assertThatThrownBy(() -> service.openRound(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                ROUND_ID
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception ->
                        ((CustomException) exception)
                                .getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode
                        .REVIEW_ROUND_OPEN_WINDOW_EXPIRED);

        verifyNoInteractions(
                reviewCriterionRepository,
                reviewRoundEntryRepository);
    }

    private void stubRoundOrganization() {
        given(reviewRoundRepository.findOrganizationIdById(ROUND_ID))
                .willReturn(Optional.of(ORGANIZATION_ID));
    }

    private ReviewRoundSaveReq request() {
        return request(ReviewRoundTargetType.ALL_SUBMISSIONS);
    }

    private ReviewRoundSaveReq request(
            ReviewRoundTargetType targetType
    ) {
        return new ReviewRoundSaveReq(
                1,
                "예선 심사",
                STARTS_AT,
                ENDS_AT,
                targetType,
                ReviewRoundDecisionRule.TOP_N,
                10,
                null,
                List.of(new ReviewRoundCriterionReq(
                        null,
                        "creativity",
                        "창의성",
                        50,
                        1
                ))
        );
    }

    private ReviewRound round(ReviewRoundStatus status) {
        ReviewRound round = ReviewRound.builder()
                .contest(contest)
                .roundNo(1)
                .name("예선 심사")
                .status(status)
                .startsAt(STARTS_AT)
                .endsAt(ENDS_AT)
                .targetType(ReviewRoundTargetType.ALL_SUBMISSIONS)
                .decisionRule(ReviewRoundDecisionRule.TOP_N)
                .selectCount(10)
                .build();
        ReflectionTestUtils.setField(round, "id", ROUND_ID);
        return round;
    }

    private ReviewCriterion criterion(ReviewRound round) {
        ReviewCriterion criterion = ReviewCriterion.builder()
                .reviewRound(round)
                .code("creativity")
                .label("창의성")
                .maxScore(50)
                .sortOrder(1)
                .active(true)
                .build();
        ReflectionTestUtils.setField(criterion, "id", 50L);
        return criterion;
    }

    private ReviewRoundEntry entry(ReviewRound round, Long id) {
        return entry(round, null, id);
    }

    private ReviewRoundEntry entry(
            ReviewRound round,
            Submission submission,
            Long id
    ) {
        ReviewRoundEntry entry = ReviewRoundEntry.builder()
                .reviewRound(round)
                .submission(submission)
                .status(ReviewRoundEntryStatus.ELIGIBLE)
                .build();
        ReflectionTestUtils.setField(entry, "id", id);
        return entry;
    }

    private Team approvedTeam() {
        Team team = Team.builder()
                .contest(contest)
                .leaderUser(admin)
                .name("트랙키 팀")
                .leaderName("참가자")
                .major("컴퓨터공학부")
                .memberCount(1)
                .status(TeamStatus.APPROVED)
                .contactEmail("participant@example.com")
                .phone("010-1234-5678")
                .motivation("학교 문제를 해결합니다.")
                .build();
        ReflectionTestUtils.setField(team, "id", 200L);
        team.finalizeParticipation(UTC_NOW.minusDays(1));
        return team;
    }

    private Submission submittedSubmission(
            Team team,
            Long id,
            String publicId
    ) {
        Submission submission = Submission.builder()
                .publicId(publicId)
                .team(team)
                .title("AI 캠퍼스")
                .status(SubmissionStatus.SUBMITTED)
                .submittedAt(NOW.minusDays(1))
                .build();
        ReflectionTestUtils.setField(submission, "id", id);
        return submission;
    }

    private ReviewAssignment assignment(
            ReviewRoundEntry entry,
            Long id,
            ReviewAssignmentStatus status
    ) {
        ReviewAssignment assignment = ReviewAssignment.builder()
                .reviewRoundEntry(entry)
                .status(status)
                .assignedAt(NOW.minusHours(1))
                .completedAt(status == ReviewAssignmentStatus.COMPLETED
                        ? NOW.minusMinutes(10)
                        : null)
                .build();
        ReflectionTestUtils.setField(assignment, "id", id);
        return assignment;
    }
}
