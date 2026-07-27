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
import com.api.trekkey.domain.contest.entity.ReviewCriterion;
import com.api.trekkey.domain.contest.entity.StagePassRule;
import com.api.trekkey.domain.contest.entity.StageStatus;
import com.api.trekkey.domain.contest.entity.StageTargetType;
import com.api.trekkey.domain.contest.entity.StageType;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.contest.repository.ContestStageRepository;
import com.api.trekkey.domain.contest.repository.ReviewCriterionRepository;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.review.entity.ReviewRoundEntry;
import com.api.trekkey.domain.review.entity.ReviewRoundEntryStatus;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.repository.ReviewRoundEntryRepository;
import com.api.trekkey.domain.review.web.dto.response.ReviewRoundEntryRes;
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
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReviewRoundEntryAdminServiceImplTest {

    private static final Long ADMIN_ID = 10L;
    private static final Long ORGANIZATION_ID = 1L;
    private static final Long CONTEST_ID = 100L;
    private static final Long SUBMISSION_STAGE_ID = 201L;
    private static final Long REVIEW_STAGE_ID = 202L;
    private static final Long TEAM_ID = 301L;
    private static final Long SUBMISSION_ID = 401L;
    private static final String CONTEST_PUBLIC_ID = "contest-public-id";
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 7, 24, 12, 0);

    @Mock
    private UserRepository userRepository;

    @Mock
    private ContestRepository contestRepository;

    @Mock
    private ContestStageRepository contestStageRepository;

    @Mock
    private ReviewCriterionRepository reviewCriterionRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private ReviewRoundEntryRepository reviewRoundEntryRepository;

    @Mock
    private AdminAuditLogger adminAuditLogger;

    @Mock
    private EntityManager entityManager;

    private ReviewRoundEntryAdminServiceImpl service;
    private Organization organization;
    private User admin;
    private Contest contest;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-24T03:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        service = new ReviewRoundEntryAdminServiceImpl(
                userRepository,
                contestRepository,
                contestStageRepository,
                reviewCriterionRepository,
                teamRepository,
                submissionRepository,
                reviewRoundEntryRepository,
                adminAuditLogger,
                clock,
                entityManager
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
    }

    @Test
    @DisplayName("ALL_SUBMISSIONS 대상을 준비하면 제출물을 잠그고 ELIGIBLE 엔트리와 감사 로그를 생성한다")
    @SuppressWarnings("unchecked")
    void prepareEntries_finalizesSubmissionAndCreatesEligibleEntry() {
        ContestStage submissionStage = submissionStage(StageStatus.COMPLETED);
        ContestStage reviewStage =
                reviewStage(StageTargetType.ALL_SUBMISSIONS, StageStatus.PREPARING);
        Team team = approvedTeam();
        Submission submission = submittedSubmission(team);
        stubAdminContestAndLockedStages(submissionStage, reviewStage);
        given(teamRepository.findAllForUpdateByContestIdOrderByIdAsc(CONTEST_ID))
                .willReturn(List.of(team));
        given(submissionRepository
                .findAllForUpdateByContestIdAndStatusAndTeamStatus(
                        CONTEST_ID,
                        SubmissionStatus.SUBMITTED,
                        TeamStatus.APPROVED
                )).willReturn(List.of(submission));
        given(reviewRoundEntryRepository
                .findAllForShareByReviewStageIdOrderByCreatedAtAscIdAsc(
                        REVIEW_STAGE_ID))
                .willReturn(List.of());
        given(reviewRoundEntryRepository.saveAllAndFlush(anyList()))
                .willAnswer(invocation -> {
                    List<ReviewRoundEntry> entries = invocation.getArgument(0);
                    ReflectionTestUtils.setField(entries.getFirst(), "id", 501L);
                    return entries;
                });

        List<ReviewRoundEntryRes> response = service.prepareEntries(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                REVIEW_STAGE_ID
        );

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().id()).isEqualTo(501L);
        assertThat(response.getFirst().status())
                .isEqualTo(ReviewRoundEntryStatus.ELIGIBLE);
        assertThat(response.getFirst().reviewStageId()).isEqualTo(REVIEW_STAGE_ID);
        assertThat(response.getFirst().submissionPublicId())
                .isEqualTo("submission-public-id");
        assertThat(response.getFirst().submissionFinalizedAt()).isEqualTo(NOW);
        assertThat(submission.getFinalizedAt()).isEqualTo(NOW);

        ArgumentCaptor<List<ReviewRoundEntry>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(reviewRoundEntryRepository).saveAllAndFlush(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(entry -> {
            assertThat(entry.getReviewStage()).isSameAs(reviewStage);
            assertThat(entry.getSubmission()).isSameAs(submission);
            assertThat(entry.getStatus()).isEqualTo(ReviewRoundEntryStatus.ELIGIBLE);
        });

        InOrder lockOrder = inOrder(
                contestStageRepository,
                reviewCriterionRepository,
                reviewRoundEntryRepository,
                teamRepository,
                submissionRepository
        );
        lockOrder.verify(contestStageRepository)
                .findAllForUpdateByContestIdOrderBySequenceNoAsc(CONTEST_ID);
        lockOrder.verify(reviewCriterionRepository)
                .findAllForUpdateByContestStageIdInOrderBySortOrderAsc(
                        List.of(REVIEW_STAGE_ID));
        lockOrder.verify(reviewRoundEntryRepository)
                .findAllForShareByReviewStageIdOrderByCreatedAtAscIdAsc(
                        REVIEW_STAGE_ID);
        lockOrder.verify(teamRepository)
                .findAllForUpdateByContestIdOrderByIdAsc(CONTEST_ID);
        lockOrder.verify(submissionRepository)
                .findAllForUpdateByContestIdAndStatusAndTeamStatus(
                        CONTEST_ID,
                        SubmissionStatus.SUBMITTED,
                        TeamStatus.APPROVED
                );
        verify(entityManager).refresh(
                contest,
                LockModeType.PESSIMISTIC_READ
        );

        verify(adminAuditLogger).log(
                ADMIN_ID,
                ORGANIZATION_ID,
                AuditAction.REVIEW_ENTRIES_PREPARE,
                "REVIEW_STAGE",
                REVIEW_STAGE_ID,
                "contestId=100, entryCount=1"
        );
    }

    @Test
    @DisplayName("비활성 관리자는 심사 대상 API를 사용할 수 없다")
    void prepareEntries_rejectsInactiveAdmin() {
        ReflectionTestUtils.setField(
                admin,
                "status",
                UserStatus.INACTIVE
        );
        given(userRepository.findById(ADMIN_ID))
                .willReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.prepareEntries(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                REVIEW_STAGE_ID
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e)
                        .getBaseResponseCode())
                .isEqualTo(UserErrorResponseCode.USER_INVALID_TOKEN);

        verifyNoInteractions(
                contestRepository,
                contestStageRepository,
                reviewCriterionRepository,
                teamRepository,
                submissionRepository,
                reviewRoundEntryRepository
        );
    }

    @Test
    @DisplayName("다른 조직의 대회에는 심사 대상을 준비할 수 없다")
    void prepareEntries_rejectsContestFromAnotherOrganization() {
        Organization otherOrganization =
                org.mockito.Mockito.mock(Organization.class);
        given(otherOrganization.getId()).willReturn(2L);
        ReflectionTestUtils.setField(
                contest,
                "organization",
                otherOrganization
        );
        given(userRepository.findById(ADMIN_ID))
                .willReturn(Optional.of(admin));
        given(contestRepository.findByPublicId(CONTEST_PUBLIC_ID))
                .willReturn(Optional.of(contest));

        assertThatThrownBy(() -> service.prepareEntries(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                REVIEW_STAGE_ID
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e)
                        .getBaseResponseCode())
                .isEqualTo(ContestErrorResponseCode.CONTEST_FORBIDDEN);

        verifyNoInteractions(
                contestStageRepository,
                reviewCriterionRepository,
                teamRepository,
                submissionRepository,
                reviewRoundEntryRepository
        );
    }

    @Test
    @DisplayName("같은 조직이어도 URL의 대회에 속하지 않은 단계는 사용할 수 없다")
    void prepareEntries_rejectsStageFromAnotherContest() {
        ContestStage submissionStage =
                submissionStage(StageStatus.COMPLETED);
        stubAdminAndContest();
        given(contestStageRepository
                .findOrganizationIdById(REVIEW_STAGE_ID))
                .willReturn(Optional.of(ORGANIZATION_ID));
        given(contestStageRepository
                .findAllForUpdateByContestIdOrderBySequenceNoAsc(CONTEST_ID))
                .willReturn(List.of(submissionStage));

        assertThatThrownBy(() -> service.prepareEntries(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                REVIEW_STAGE_ID
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e)
                        .getBaseResponseCode())
                .isEqualTo(ContestErrorResponseCode.STAGE_NOT_FOUND);

        verifyNoInteractions(
                reviewCriterionRepository,
                teamRepository,
                submissionRepository,
                reviewRoundEntryRepository
        );
    }

    @Test
    @DisplayName("이미 준비된 심사 대상은 재호출해도 다시 잠그거나 생성하지 않는다")
    void prepareEntries_returnsExistingEntriesIdempotently() {
        ContestStage submissionStage = submissionStage(StageStatus.COMPLETED);
        ContestStage reviewStage =
                reviewStage(StageTargetType.ALL_SUBMISSIONS, StageStatus.PREPARING);
        Team team = approvedTeam();
        Submission submission = submittedSubmission(team);
        assertThat(submission.finalizeAt(NOW.minusHours(1))).isTrue();
        ReviewRoundEntry existingEntry = entry(501L, reviewStage, submission);
        stubAdminContestAndLockedStages(submissionStage, reviewStage);
        given(reviewRoundEntryRepository
                .findAllForShareByReviewStageIdOrderByCreatedAtAscIdAsc(
                        REVIEW_STAGE_ID))
                .willReturn(List.of(existingEntry));

        List<ReviewRoundEntryRes> response = service.prepareEntries(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                REVIEW_STAGE_ID
        );

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().id()).isEqualTo(501L);
        assertThat(submission.getFinalizedAt()).isEqualTo(NOW.minusHours(1));
        verify(reviewRoundEntryRepository, never()).saveAllAndFlush(anyList());
        verify(adminAuditLogger, never()).log(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    @DisplayName("ALL_SUBMISSIONS가 아닌 대상 방식은 지원하지 않는 오류로 거부한다")
    void prepareEntries_rejectsUnsupportedTargetType() {
        ContestStage submissionStage = submissionStage(StageStatus.COMPLETED);
        ContestStage reviewStage =
                reviewStage(StageTargetType.PREVIOUS_PASSED, StageStatus.PREPARING);
        stubAdminContestAndLockedStages(submissionStage, reviewStage);

        assertReviewError(
                () -> service.prepareEntries(
                        ADMIN_ID,
                        CONTEST_PUBLIC_ID,
                        REVIEW_STAGE_ID
                ),
                ReviewErrorResponseCode.REVIEW_ENTRY_TARGET_TYPE_UNSUPPORTED
        );

        verifyNoInteractions(teamRepository, submissionRepository);
        verify(reviewRoundEntryRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("준비 중이 아닌 심사 단계에는 대상을 생성할 수 없다")
    void prepareEntries_rejectsReviewStageThatIsNotPreparing() {
        ContestStage submissionStage = submissionStage(StageStatus.COMPLETED);
        ContestStage reviewStage =
                reviewStage(StageTargetType.ALL_SUBMISSIONS, StageStatus.OPEN);
        stubAdminContestAndLockedStages(submissionStage, reviewStage);
        given(reviewRoundEntryRepository
                .findAllForShareByReviewStageIdOrderByCreatedAtAscIdAsc(
                        REVIEW_STAGE_ID))
                .willReturn(List.of());

        assertReviewError(
                () -> service.prepareEntries(
                        ADMIN_ID,
                        CONTEST_PUBLIC_ID,
                        REVIEW_STAGE_ID
                ),
                ReviewErrorResponseCode.REVIEW_ENTRY_PREPARATION_NOT_ALLOWED
        );

        verifyNoInteractions(teamRepository, submissionRepository);
        verify(reviewRoundEntryRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("대회가 REVIEWING 상태가 아니면 새 심사 대상을 준비할 수 없다")
    void prepareEntries_rejectsContestThatIsNotReviewing() {
        ReflectionTestUtils.setField(
                contest,
                "status",
                ContestStatus.AWARDED
        );
        ContestStage submissionStage =
                submissionStage(StageStatus.COMPLETED);
        ContestStage reviewStage =
                reviewStage(
                        StageTargetType.ALL_SUBMISSIONS,
                        StageStatus.PREPARING
                );
        stubAdminContestAndLockedStages(submissionStage, reviewStage);
        given(reviewRoundEntryRepository
                .findAllForShareByReviewStageIdOrderByCreatedAtAscIdAsc(
                        REVIEW_STAGE_ID))
                .willReturn(List.of());

        assertReviewError(
                () -> service.prepareEntries(
                        ADMIN_ID,
                        CONTEST_PUBLIC_ID,
                        REVIEW_STAGE_ID
                ),
                ReviewErrorResponseCode
                        .REVIEW_ENTRY_CONTEST_NOT_REVIEWING
        );

        verify(entityManager).refresh(
                contest,
                LockModeType.PESSIMISTIC_READ
        );
        verifyNoInteractions(teamRepository, submissionRepository);
        verify(reviewRoundEntryRepository, never())
                .saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("제출 단계가 완료되지 않았으면 심사 대상을 준비하지 않는다")
    void prepareEntries_rejectsIncompleteSubmissionStage() {
        ContestStage submissionStage = submissionStage(StageStatus.OPEN);
        ContestStage reviewStage =
                reviewStage(StageTargetType.ALL_SUBMISSIONS, StageStatus.PREPARING);
        stubAdminContestAndLockedStages(submissionStage, reviewStage);
        given(reviewRoundEntryRepository
                .findAllForShareByReviewStageIdOrderByCreatedAtAscIdAsc(
                        REVIEW_STAGE_ID))
                .willReturn(List.of());

        assertReviewError(
                () -> service.prepareEntries(
                        ADMIN_ID,
                        CONTEST_PUBLIC_ID,
                        REVIEW_STAGE_ID
                ),
                ReviewErrorResponseCode
                        .REVIEW_ENTRY_SUBMISSION_STAGE_NOT_COMPLETED
        );

        verifyNoInteractions(teamRepository, submissionRepository);
        verify(reviewRoundEntryRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("활성 평가 기준이 없으면 대상을 고정하지 않고 설정을 수정할 수 있게 둔다")
    void prepareEntries_rejectsReviewStageWithoutActiveCriterion() {
        ContestStage submissionStage =
                submissionStage(StageStatus.COMPLETED);
        ContestStage reviewStage =
                reviewStage(
                        StageTargetType.ALL_SUBMISSIONS,
                        StageStatus.PREPARING
                );
        stubAdminContestAndLockedStages(submissionStage, reviewStage);
        given(reviewCriterionRepository
                .findAllForUpdateByContestStageIdInOrderBySortOrderAsc(
                        List.of(REVIEW_STAGE_ID)))
                .willReturn(List.of());
        given(reviewRoundEntryRepository
                .findAllForShareByReviewStageIdOrderByCreatedAtAscIdAsc(
                        REVIEW_STAGE_ID))
                .willReturn(List.of());

        assertThatThrownBy(() -> service.prepareEntries(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                REVIEW_STAGE_ID
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e)
                        .getBaseResponseCode())
                .isEqualTo(
                        ContestErrorResponseCode.REVIEW_CRITERION_REQUIRED);

        verifyNoInteractions(teamRepository, submissionRepository);
        verify(reviewRoundEntryRepository, never())
                .saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("오픈할 수 없는 라운드 설정이면 심사 대상을 만들기 전에 거부한다")
    void prepareEntries_rejectsInvalidReviewStageConfiguration() {
        ContestStage submissionStage =
                submissionStage(StageStatus.COMPLETED);
        ContestStage reviewStage =
                reviewStage(
                        StageTargetType.ALL_SUBMISSIONS,
                        StageStatus.PREPARING
                );
        reviewStage.updateConfiguration(
                reviewStage.getName(),
                reviewStage.getStageType(),
                reviewStage.getSequenceNo(),
                reviewStage.getStartsAt(),
                reviewStage.getEndsAt(),
                reviewStage.getTargetType(),
                null,
                null,
                null
        );
        stubAdminContestAndLockedStages(submissionStage, reviewStage);
        given(reviewRoundEntryRepository
                .findAllForShareByReviewStageIdOrderByCreatedAtAscIdAsc(
                        REVIEW_STAGE_ID))
                .willReturn(List.of());

        assertThatThrownBy(() -> service.prepareEntries(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                REVIEW_STAGE_ID
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e)
                        .getBaseResponseCode())
                .isEqualTo(
                        ContestErrorResponseCode.STAGE_CONFIGURATION_INVALID);

        verifyNoInteractions(teamRepository, submissionRepository);
        verify(reviewRoundEntryRepository, never())
                .saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("제출 완료 후보가 없으면 빈 라운드를 만들지 않는다")
    void prepareEntries_rejectsWhenNoEligibleSubmissionsExist() {
        ContestStage submissionStage = submissionStage(StageStatus.COMPLETED);
        ContestStage reviewStage =
                reviewStage(StageTargetType.ALL_SUBMISSIONS, StageStatus.PREPARING);
        Team team = approvedTeam();
        stubAdminContestAndLockedStages(submissionStage, reviewStage);
        given(reviewRoundEntryRepository
                .findAllForShareByReviewStageIdOrderByCreatedAtAscIdAsc(
                        REVIEW_STAGE_ID))
                .willReturn(List.of());
        given(teamRepository.findAllForUpdateByContestIdOrderByIdAsc(CONTEST_ID))
                .willReturn(List.of(team));
        given(submissionRepository
                .findAllForUpdateByContestIdAndStatusAndTeamStatus(
                        CONTEST_ID,
                        SubmissionStatus.SUBMITTED,
                        TeamStatus.APPROVED
                )).willReturn(List.of());

        assertReviewError(
                () -> service.prepareEntries(
                        ADMIN_ID,
                        CONTEST_PUBLIC_ID,
                        REVIEW_STAGE_ID
                ),
                ReviewErrorResponseCode.REVIEW_ENTRY_SUBMISSION_REQUIRED
        );

        verify(reviewRoundEntryRepository, never()).saveAllAndFlush(anyList());
        verify(adminAuditLogger, never()).log(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    @DisplayName("이전 라운드에서 이미 확정된 제출물은 확정 시각을 유지한 채 다음 심사 대상이 된다")
    void prepareEntries_preservesExistingSubmissionFinalization() {
        ContestStage submissionStage =
                submissionStage(StageStatus.COMPLETED);
        ContestStage reviewStage =
                reviewStage(
                        StageTargetType.ALL_SUBMISSIONS,
                        StageStatus.PREPARING
                );
        Team team = approvedTeam();
        Submission submission = submittedSubmission(team);
        LocalDateTime originalFinalizedAt = NOW.minusHours(2);
        assertThat(submission.finalizeAt(originalFinalizedAt)).isTrue();
        stubAdminContestAndLockedStages(submissionStage, reviewStage);
        given(reviewRoundEntryRepository
                .findAllForShareByReviewStageIdOrderByCreatedAtAscIdAsc(
                        REVIEW_STAGE_ID))
                .willReturn(List.of());
        given(teamRepository
                .findAllForUpdateByContestIdOrderByIdAsc(CONTEST_ID))
                .willReturn(List.of(team));
        given(submissionRepository
                .findAllForUpdateByContestIdAndStatusAndTeamStatus(
                        CONTEST_ID,
                        SubmissionStatus.SUBMITTED,
                        TeamStatus.APPROVED
                )).willReturn(List.of(submission));
        given(reviewRoundEntryRepository.saveAllAndFlush(anyList()))
                .willAnswer(invocation -> {
                    List<ReviewRoundEntry> entries =
                            invocation.getArgument(0);
                    ReflectionTestUtils.setField(
                            entries.getFirst(),
                            "id",
                            501L
                    );
                    return entries;
                });

        List<ReviewRoundEntryRes> response = service.prepareEntries(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                REVIEW_STAGE_ID
        );

        assertThat(response.getFirst().submissionFinalizedAt())
                .isEqualTo(originalFinalizedAt);
        assertThat(submission.getFinalizedAt())
                .isEqualTo(originalFinalizedAt);
    }

    @Test
    @DisplayName("DB 유니크 제약과 충돌하면 중복 심사 대상 오류로 변환한다")
    void prepareEntries_mapsUniqueConstraintConflict() {
        ContestStage submissionStage =
                submissionStage(StageStatus.COMPLETED);
        ContestStage reviewStage =
                reviewStage(
                        StageTargetType.ALL_SUBMISSIONS,
                        StageStatus.PREPARING
                );
        Team team = approvedTeam();
        Submission submission = submittedSubmission(team);
        stubAdminContestAndLockedStages(submissionStage, reviewStage);
        given(reviewRoundEntryRepository
                .findAllForShareByReviewStageIdOrderByCreatedAtAscIdAsc(
                        REVIEW_STAGE_ID))
                .willReturn(List.of());
        given(teamRepository
                .findAllForUpdateByContestIdOrderByIdAsc(CONTEST_ID))
                .willReturn(List.of(team));
        given(submissionRepository
                .findAllForUpdateByContestIdAndStatusAndTeamStatus(
                        CONTEST_ID,
                        SubmissionStatus.SUBMITTED,
                        TeamStatus.APPROVED
                )).willReturn(List.of(submission));
        given(reviewRoundEntryRepository.saveAllAndFlush(anyList()))
                .willThrow(new DataIntegrityViolationException("duplicate"));

        assertReviewError(
                () -> service.prepareEntries(
                        ADMIN_ID,
                        CONTEST_PUBLIC_ID,
                        REVIEW_STAGE_ID
                ),
                ReviewErrorResponseCode.REVIEW_ENTRY_DUPLICATED
        );

        verify(adminAuditLogger, never()).log(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    @DisplayName("심사 대상 목록은 저장 순서대로 안전한 제출 정보와 함께 반환한다")
    void getEntries_returnsEntriesInRepositoryOrder() {
        ContestStage reviewStage =
                reviewStage(StageTargetType.ALL_SUBMISSIONS, StageStatus.OPEN);
        Team firstTeam = approvedTeam();
        Team secondTeam = approvedTeam();
        ReflectionTestUtils.setField(secondTeam, "id", 302L);
        ReflectionTestUtils.setField(secondTeam, "name", "두 번째 팀");
        Submission firstSubmission = submittedSubmission(firstTeam);
        Submission secondSubmission = submittedSubmission(secondTeam);
        ReflectionTestUtils.setField(secondSubmission, "id", 402L);
        ReflectionTestUtils.setField(
                secondSubmission,
                "publicId",
                "second-submission-public-id"
        );
        ReflectionTestUtils.setField(secondSubmission, "title", "두 번째 작품");
        ReviewRoundEntry firstEntry =
                entry(501L, reviewStage, firstSubmission);
        ReviewRoundEntry secondEntry =
                entry(502L, reviewStage, secondSubmission);
        stubAdminAndContest();
        given(contestStageRepository.findOrganizationIdById(REVIEW_STAGE_ID))
                .willReturn(Optional.of(ORGANIZATION_ID));
        given(contestStageRepository.findById(REVIEW_STAGE_ID))
                .willReturn(Optional.of(reviewStage));
        given(reviewRoundEntryRepository
                .findAllByReviewStageIdOrderByCreatedAtAscIdAsc(REVIEW_STAGE_ID))
                .willReturn(List.of(firstEntry, secondEntry));

        List<ReviewRoundEntryRes> response = service.getEntries(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                REVIEW_STAGE_ID
        );

        assertThat(response).extracting(ReviewRoundEntryRes::id)
                .containsExactly(501L, 502L);
        assertThat(response).extracting(ReviewRoundEntryRes::submissionPublicId)
                .containsExactly(
                        "submission-public-id",
                        "second-submission-public-id"
                );
        assertThat(response).extracting(ReviewRoundEntryRes::teamName)
                .containsExactly("트랙키 팀", "두 번째 팀");
        verifyNoInteractions(teamRepository, submissionRepository);
        verify(adminAuditLogger, never()).log(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    private void stubAdminAndContest() {
        given(userRepository.findById(ADMIN_ID)).willReturn(Optional.of(admin));
        given(contestRepository.findByPublicId(CONTEST_PUBLIC_ID))
                .willReturn(Optional.of(contest));
    }

    private void stubAdminContestAndLockedStages(
            ContestStage submissionStage,
            ContestStage reviewStage
    ) {
        stubAdminAndContest();
        given(contestStageRepository.findOrganizationIdById(REVIEW_STAGE_ID))
                .willReturn(Optional.of(ORGANIZATION_ID));
        given(contestStageRepository
                .findAllForUpdateByContestIdOrderBySequenceNoAsc(CONTEST_ID))
                .willReturn(List.of(submissionStage, reviewStage));
        ReviewCriterion criterion = ReviewCriterion.builder()
                .contestStage(reviewStage)
                .code("creativity")
                .label("창의성")
                .maxScore(30)
                .sortOrder(1)
                .active(true)
                .build();
        ReflectionTestUtils.setField(criterion, "id", 601L);
        org.mockito.Mockito.lenient()
                .when(reviewCriterionRepository
                        .findAllForUpdateByContestStageIdInOrderBySortOrderAsc(
                                List.of(REVIEW_STAGE_ID)))
                .thenReturn(List.of(criterion));
    }

    private ContestStage submissionStage(StageStatus status) {
        ContestStage stage = ContestStage.builder()
                .contest(contest)
                .name("작품 제출")
                .stageType(StageType.SUBMISSION)
                .sequenceNo(1)
                .status(status)
                .build();
        ReflectionTestUtils.setField(stage, "id", SUBMISSION_STAGE_ID);
        return stage;
    }

    private ContestStage reviewStage(
            StageTargetType targetType,
            StageStatus status
    ) {
        ContestStage stage = ContestStage.builder()
                .contest(contest)
                .name("1차 심사")
                .stageType(StageType.REVIEW)
                .sequenceNo(2)
                .status(status)
                .targetType(targetType)
                .passRule(StagePassRule.FINAL)
                .build();
        ReflectionTestUtils.setField(stage, "id", REVIEW_STAGE_ID);
        return stage;
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
        ReflectionTestUtils.setField(team, "id", TEAM_ID);
        return team;
    }

    private Submission submittedSubmission(Team team) {
        Submission submission = Submission.builder()
                .publicId("submission-public-id")
                .team(team)
                .title("AI 캠퍼스")
                .status(SubmissionStatus.SUBMITTED)
                .submittedAt(NOW.minusDays(1))
                .build();
        ReflectionTestUtils.setField(submission, "id", SUBMISSION_ID);
        return submission;
    }

    private ReviewRoundEntry entry(
            Long id,
            ContestStage reviewStage,
            Submission submission
    ) {
        ReviewRoundEntry entry = ReviewRoundEntry.builder()
                .reviewStage(reviewStage)
                .submission(submission)
                .status(ReviewRoundEntryStatus.ELIGIBLE)
                .build();
        ReflectionTestUtils.setField(entry, "id", id);
        return entry;
    }

    private void assertReviewError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
            ReviewErrorResponseCode expectedCode
    ) {
        assertThatThrownBy(callable)
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(expectedCode);
    }
}
