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
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.review.entity.ReviewCriterion;
import com.api.trekkey.domain.review.entity.ReviewRound;
import com.api.trekkey.domain.review.entity.ReviewRoundDecisionRule;
import com.api.trekkey.domain.review.entity.ReviewRoundEntry;
import com.api.trekkey.domain.review.entity.ReviewRoundEntryStatus;
import com.api.trekkey.domain.review.entity.ReviewRoundStatus;
import com.api.trekkey.domain.review.entity.ReviewRoundTargetType;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.repository.ReviewCriterionRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundEntryRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundRepository;
import com.api.trekkey.domain.review.admin.web.dto.response.ReviewRoundEntryRes;
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
import java.time.LocalDateTime;
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
    private static final Long REVIEW_ROUND_ID = 202L;
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
    private ReviewRoundRepository reviewRoundRepository;

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

    private ReviewRoundEntryAdminServiceImpl service;
    private Organization organization;
    private User admin;
    private Contest contest;

    @BeforeEach
    void setUp() {
        service = new ReviewRoundEntryAdminServiceImpl(
                userRepository,
                contestRepository,
                reviewRoundRepository,
                reviewCriterionRepository,
                teamRepository,
                submissionRepository,
                reviewRoundEntryRepository,
                adminAuditLogger
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
    @DisplayName("ALL_SUBMISSIONS 대상을 준비하면 제출물은 열어 둔 채 ELIGIBLE 엔트리와 감사 로그를 생성한다")
    @SuppressWarnings("unchecked")
    void prepareEntries_createsEligibleEntryWithoutFinalizingSubmission() {
        ReviewRound reviewRound = reviewRound(
                ReviewRoundTargetType.ALL_SUBMISSIONS,
                ReviewRoundStatus.PREPARING
        );
        Team team = approvedTeam();
        Submission submission = submittedSubmission(team);
        stubAdminContestAndLockedRound(reviewRound);
        given(teamRepository.findAllForUpdateByContestIdOrderByIdAsc(CONTEST_ID))
                .willReturn(List.of(team));
        given(submissionRepository
                .findAllForUpdateByContestIdAndStatusAndTeamStatus(
                        CONTEST_ID,
                        SubmissionStatus.SUBMITTED,
                        TeamStatus.APPROVED
                )).willReturn(List.of(submission));
        given(reviewRoundEntryRepository
                .findAllForShareByReviewRoundIdOrderByCreatedAtAscIdAsc(
                        REVIEW_ROUND_ID))
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
                REVIEW_ROUND_ID
        );

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().id()).isEqualTo(501L);
        assertThat(response.getFirst().status())
                .isEqualTo(ReviewRoundEntryStatus.ELIGIBLE);
        assertThat(response.getFirst().reviewRoundId()).isEqualTo(REVIEW_ROUND_ID);
        assertThat(response.getFirst().submissionPublicId())
                .isEqualTo("submission-public-id");
        assertThat(response.getFirst().submissionFinalizedAt()).isNull();
        assertThat(submission.getFinalizedAt()).isNull();

        ArgumentCaptor<List<ReviewRoundEntry>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(reviewRoundEntryRepository).saveAllAndFlush(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(entry -> {
            assertThat(entry.getReviewRound()).isSameAs(reviewRound);
            assertThat(entry.getSubmission()).isSameAs(submission);
            assertThat(entry.getStatus()).isEqualTo(ReviewRoundEntryStatus.ELIGIBLE);
        });

        InOrder lockOrder = inOrder(
                reviewRoundRepository,
                reviewCriterionRepository,
                reviewRoundEntryRepository,
                teamRepository,
                submissionRepository
        );
        lockOrder.verify(reviewRoundRepository)
                .findByIdForUpdate(REVIEW_ROUND_ID);
        lockOrder.verify(reviewCriterionRepository)
                .findAllForUpdateByReviewRoundIdInOrderBySortOrderAsc(
                        List.of(REVIEW_ROUND_ID));
        lockOrder.verify(reviewRoundEntryRepository)
                .findAllForShareByReviewRoundIdOrderByCreatedAtAscIdAsc(
                        REVIEW_ROUND_ID);
        lockOrder.verify(teamRepository)
                .findAllForUpdateByContestIdOrderByIdAsc(CONTEST_ID);
        lockOrder.verify(submissionRepository)
                .findAllForUpdateByContestIdAndStatusAndTeamStatus(
                        CONTEST_ID,
                        SubmissionStatus.SUBMITTED,
                        TeamStatus.APPROVED
                );
        verify(adminAuditLogger).log(
                ADMIN_ID,
                ORGANIZATION_ID,
                AuditAction.REVIEW_ENTRIES_PREPARE,
                "REVIEW_ROUND",
                REVIEW_ROUND_ID,
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
                REVIEW_ROUND_ID
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e)
                        .getBaseResponseCode())
                .isEqualTo(UserErrorResponseCode.USER_INVALID_TOKEN);

        verifyNoInteractions(
                contestRepository,
                reviewRoundRepository,
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
                REVIEW_ROUND_ID
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e)
                        .getBaseResponseCode())
                .isEqualTo(ContestErrorResponseCode.CONTEST_FORBIDDEN);

        verifyNoInteractions(
                reviewRoundRepository,
                reviewCriterionRepository,
                teamRepository,
                submissionRepository,
                reviewRoundEntryRepository
        );
    }

    @Test
    @DisplayName("같은 조직이어도 URL의 대회에 속하지 않은 리뷰 라운드는 사용할 수 없다")
    void prepareEntries_rejectsRoundFromAnotherContest() {
        Contest otherContest = Contest.builder()
                .publicId("other-contest")
                .organization(organization)
                .ownerUser(admin)
                .title("다른 공모전")
                .department("교무처")
                .status(ContestStatus.REVIEWING)
                .participationType(ParticipationType.TEAM)
                .awardCount(1)
                .summary("다른 공모전")
                .target("재학생")
                .applicationMethod("온라인")
                .benefits("상장")
                .detailHtml("<p>본문</p>")
                .build();
        ReflectionTestUtils.setField(otherContest, "id", 999L);
        ReviewRound otherRound = reviewRound(
                otherContest,
                ReviewRoundTargetType.ALL_SUBMISSIONS,
                ReviewRoundStatus.PREPARING
        );
        stubAdminAndContest();
        given(reviewRoundRepository
                .findOrganizationIdById(REVIEW_ROUND_ID))
                .willReturn(Optional.of(ORGANIZATION_ID));
        given(reviewRoundRepository.findByIdForUpdate(REVIEW_ROUND_ID))
                .willReturn(Optional.of(otherRound));

        assertThatThrownBy(() -> service.prepareEntries(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                REVIEW_ROUND_ID
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e)
                        .getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode.REVIEW_ROUND_NOT_FOUND);

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
        ReviewRound reviewRound = reviewRound(
                ReviewRoundTargetType.ALL_SUBMISSIONS,
                ReviewRoundStatus.PREPARING
        );
        Team team = approvedTeam();
        Submission submission = submittedSubmission(team);
        assertThat(submission.finalizeAt(NOW.minusHours(1))).isTrue();
        ReviewRoundEntry existingEntry = entry(501L, reviewRound, submission);
        stubAdminContestAndLockedRound(reviewRound);
        given(reviewRoundEntryRepository
                .findAllForShareByReviewRoundIdOrderByCreatedAtAscIdAsc(
                        REVIEW_ROUND_ID))
                .willReturn(List.of(existingEntry));

        List<ReviewRoundEntryRes> response = service.prepareEntries(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                REVIEW_ROUND_ID
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
        ReviewRound reviewRound = reviewRound(
                ReviewRoundTargetType.PREVIOUS_SELECTED,
                ReviewRoundStatus.PREPARING
        );
        stubAdminContestAndLockedRound(reviewRound);

        assertReviewError(
                () -> service.prepareEntries(
                        ADMIN_ID,
                        CONTEST_PUBLIC_ID,
                        REVIEW_ROUND_ID
                ),
                ReviewErrorResponseCode.REVIEW_ENTRY_TARGET_TYPE_UNSUPPORTED
        );

        verifyNoInteractions(teamRepository, submissionRepository);
        verify(reviewRoundEntryRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("준비 중이 아닌 리뷰 라운드에는 대상을 생성할 수 없다")
    void prepareEntries_rejectsReviewRoundThatIsNotPreparing() {
        ReviewRound reviewRound = reviewRound(
                ReviewRoundTargetType.ALL_SUBMISSIONS,
                ReviewRoundStatus.OPEN
        );
        stubAdminContestAndLockedRound(reviewRound);
        given(reviewRoundEntryRepository
                .findAllForShareByReviewRoundIdOrderByCreatedAtAscIdAsc(
                        REVIEW_ROUND_ID))
                .willReturn(List.of());

        assertReviewError(
                () -> service.prepareEntries(
                        ADMIN_ID,
                        CONTEST_PUBLIC_ID,
                        REVIEW_ROUND_ID
                ),
                ReviewErrorResponseCode.REVIEW_ENTRY_PREPARATION_NOT_ALLOWED
        );

        verifyNoInteractions(teamRepository, submissionRepository);
        verify(reviewRoundEntryRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("심사 대상 준비 여부는 기존 대회 상태가 아니라 리뷰 라운드 상태로 판단한다")
    @SuppressWarnings("unchecked")
    void prepareEntries_doesNotDependOnLegacyContestStatus() {
        ReflectionTestUtils.setField(
                contest,
                "status",
                ContestStatus.AWARDED
        );
        ReviewRound reviewRound = reviewRound(
                ReviewRoundTargetType.ALL_SUBMISSIONS,
                ReviewRoundStatus.PREPARING
        );
        Team team = approvedTeam();
        Submission submission = submittedSubmission(team);
        stubAdminContestAndLockedRound(reviewRound);
        given(reviewRoundEntryRepository
                .findAllForShareByReviewRoundIdOrderByCreatedAtAscIdAsc(
                        REVIEW_ROUND_ID))
                .willReturn(List.of());
        given(teamRepository.findAllForUpdateByContestIdOrderByIdAsc(CONTEST_ID))
                .willReturn(List.of(team));
        given(submissionRepository
                .findAllForUpdateByContestIdAndStatusAndTeamStatus(
                        CONTEST_ID,
                        SubmissionStatus.SUBMITTED,
                        TeamStatus.APPROVED
                )).willReturn(List.of(submission));
        given(reviewRoundEntryRepository.saveAllAndFlush(anyList()))
                .willAnswer(invocation -> {
                    List<ReviewRoundEntry> entries = invocation.getArgument(0);
                    ReflectionTestUtils.setField(entries.getFirst(), "id", 501L);
                    return entries;
                });

        List<ReviewRoundEntryRes> response = service.prepareEntries(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                REVIEW_ROUND_ID
        );

        assertThat(response).extracting(ReviewRoundEntryRes::id)
                .containsExactly(501L);
    }

    @Test
    @DisplayName("별도 제출 단계 없이 제출 상태와 팀 승인만으로 심사 대상을 준비한다")
    @SuppressWarnings("unchecked")
    void prepareEntries_doesNotDependOnSubmissionStage() {
        ReviewRound reviewRound = reviewRound(
                ReviewRoundTargetType.ALL_SUBMISSIONS,
                ReviewRoundStatus.PREPARING
        );
        Team team = approvedTeam();
        Submission submission = submittedSubmission(team);
        stubAdminContestAndLockedRound(reviewRound);
        given(reviewRoundEntryRepository
                .findAllForShareByReviewRoundIdOrderByCreatedAtAscIdAsc(
                        REVIEW_ROUND_ID))
                .willReturn(List.of());
        given(teamRepository.findAllForUpdateByContestIdOrderByIdAsc(CONTEST_ID))
                .willReturn(List.of(team));
        given(submissionRepository
                .findAllForUpdateByContestIdAndStatusAndTeamStatus(
                        CONTEST_ID,
                        SubmissionStatus.SUBMITTED,
                        TeamStatus.APPROVED
                )).willReturn(List.of(submission));
        given(reviewRoundEntryRepository.saveAllAndFlush(anyList()))
                .willAnswer(invocation -> {
                    List<ReviewRoundEntry> entries = invocation.getArgument(0);
                    ReflectionTestUtils.setField(entries.getFirst(), "id", 501L);
                    return entries;
                });

        List<ReviewRoundEntryRes> response = service.prepareEntries(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                REVIEW_ROUND_ID
        );

        assertThat(response).singleElement().satisfies(entry -> {
            assertThat(entry.submissionPublicId())
                    .isEqualTo("submission-public-id");
            assertThat(entry.status())
                    .isEqualTo(ReviewRoundEntryStatus.ELIGIBLE);
        });
    }

    @Test
    @DisplayName("활성 평가 기준이 없으면 대상을 고정하지 않고 설정을 수정할 수 있게 둔다")
    void prepareEntries_rejectsReviewRoundWithoutActiveCriterion() {
        ReviewRound reviewRound = reviewRound(
                ReviewRoundTargetType.ALL_SUBMISSIONS,
                ReviewRoundStatus.PREPARING
        );
        stubAdminContestAndLockedRound(reviewRound);
        given(reviewCriterionRepository
                .findAllForUpdateByReviewRoundIdInOrderBySortOrderAsc(
                        List.of(REVIEW_ROUND_ID)))
                .willReturn(List.of());
        given(reviewRoundEntryRepository
                .findAllForShareByReviewRoundIdOrderByCreatedAtAscIdAsc(
                        REVIEW_ROUND_ID))
                .willReturn(List.of());

        assertThatThrownBy(() -> service.prepareEntries(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                REVIEW_ROUND_ID
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e)
                        .getBaseResponseCode())
                .isEqualTo(
                        ReviewErrorResponseCode
                                .REVIEW_ROUND_CRITERION_REQUIRED);

        verifyNoInteractions(teamRepository, submissionRepository);
        verify(reviewRoundEntryRepository, never())
                .saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("오픈할 수 없는 라운드 설정이면 심사 대상을 만들기 전에 거부한다")
    void prepareEntries_rejectsInvalidReviewRoundConfiguration() {
        ReviewRound reviewRound = reviewRound(
                ReviewRoundTargetType.ALL_SUBMISSIONS,
                ReviewRoundStatus.PREPARING
        );
        ReflectionTestUtils.setField(reviewRound, "decisionRule", null);
        stubAdminContestAndLockedRound(reviewRound);
        given(reviewRoundEntryRepository
                .findAllForShareByReviewRoundIdOrderByCreatedAtAscIdAsc(
                        REVIEW_ROUND_ID))
                .willReturn(List.of());

        assertThatThrownBy(() -> service.prepareEntries(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                REVIEW_ROUND_ID
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e)
                        .getBaseResponseCode())
                .isEqualTo(
                        ReviewErrorResponseCode
                                .REVIEW_ROUND_CONFIGURATION_INVALID);

        verifyNoInteractions(teamRepository, submissionRepository);
        verify(reviewRoundEntryRepository, never())
                .saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("제출 완료 후보가 없으면 빈 라운드를 만들지 않는다")
    void prepareEntries_rejectsWhenNoEligibleSubmissionsExist() {
        ReviewRound reviewRound = reviewRound(
                ReviewRoundTargetType.ALL_SUBMISSIONS,
                ReviewRoundStatus.PREPARING
        );
        Team team = approvedTeam();
        stubAdminContestAndLockedRound(reviewRound);
        given(reviewRoundEntryRepository
                .findAllForShareByReviewRoundIdOrderByCreatedAtAscIdAsc(
                        REVIEW_ROUND_ID))
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
                        REVIEW_ROUND_ID
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
        ReviewRound reviewRound = reviewRound(
                ReviewRoundTargetType.ALL_SUBMISSIONS,
                ReviewRoundStatus.PREPARING
        );
        Team team = approvedTeam();
        Submission submission = submittedSubmission(team);
        LocalDateTime originalFinalizedAt = NOW.minusHours(2);
        assertThat(submission.finalizeAt(originalFinalizedAt)).isTrue();
        stubAdminContestAndLockedRound(reviewRound);
        given(reviewRoundEntryRepository
                .findAllForShareByReviewRoundIdOrderByCreatedAtAscIdAsc(
                        REVIEW_ROUND_ID))
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
                REVIEW_ROUND_ID
        );

        assertThat(response.getFirst().submissionFinalizedAt())
                .isEqualTo(originalFinalizedAt);
        assertThat(submission.getFinalizedAt())
                .isEqualTo(originalFinalizedAt);
    }

    @Test
    @DisplayName("DB 유니크 제약과 충돌하면 중복 심사 대상 오류로 변환한다")
    void prepareEntries_mapsUniqueConstraintConflict() {
        ReviewRound reviewRound = reviewRound(
                ReviewRoundTargetType.ALL_SUBMISSIONS,
                ReviewRoundStatus.PREPARING
        );
        Team team = approvedTeam();
        Submission submission = submittedSubmission(team);
        stubAdminContestAndLockedRound(reviewRound);
        given(reviewRoundEntryRepository
                .findAllForShareByReviewRoundIdOrderByCreatedAtAscIdAsc(
                        REVIEW_ROUND_ID))
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
                        REVIEW_ROUND_ID
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
        ReviewRound reviewRound = reviewRound(
                ReviewRoundTargetType.ALL_SUBMISSIONS,
                ReviewRoundStatus.OPEN
        );
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
                entry(501L, reviewRound, firstSubmission);
        ReviewRoundEntry secondEntry =
                entry(502L, reviewRound, secondSubmission);
        stubAdminAndContest();
        given(reviewRoundRepository.findOrganizationIdById(REVIEW_ROUND_ID))
                .willReturn(Optional.of(ORGANIZATION_ID));
        given(reviewRoundRepository.findById(REVIEW_ROUND_ID))
                .willReturn(Optional.of(reviewRound));
        given(reviewRoundEntryRepository
                .findAllByReviewRoundIdOrderByCreatedAtAscIdAsc(
                        REVIEW_ROUND_ID))
                .willReturn(List.of(firstEntry, secondEntry));

        List<ReviewRoundEntryRes> response = service.getEntries(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                REVIEW_ROUND_ID
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

    private void stubAdminContestAndLockedRound(ReviewRound reviewRound) {
        stubAdminAndContest();
        given(reviewRoundRepository.findOrganizationIdById(REVIEW_ROUND_ID))
                .willReturn(Optional.of(ORGANIZATION_ID));
        given(reviewRoundRepository.findByIdForUpdate(REVIEW_ROUND_ID))
                .willReturn(Optional.of(reviewRound));
        ReviewCriterion criterion = ReviewCriterion.builder()
                .reviewRound(reviewRound)
                .code("creativity")
                .label("창의성")
                .maxScore(30)
                .sortOrder(1)
                .active(true)
                .build();
        ReflectionTestUtils.setField(criterion, "id", 601L);
        org.mockito.Mockito.lenient()
                .when(reviewCriterionRepository
                        .findAllForUpdateByReviewRoundIdInOrderBySortOrderAsc(
                                List.of(REVIEW_ROUND_ID)))
                .thenReturn(List.of(criterion));
    }

    private ReviewRound reviewRound(
            ReviewRoundTargetType targetType,
            ReviewRoundStatus status
    ) {
        return reviewRound(contest, targetType, status);
    }

    private ReviewRound reviewRound(
            Contest roundContest,
            ReviewRoundTargetType targetType,
            ReviewRoundStatus status
    ) {
        ReviewRound round = ReviewRound.builder()
                .contest(roundContest)
                .roundNo(1)
                .name("1차 심사")
                .status(status)
                .startsAt(NOW.minusHours(1))
                .endsAt(NOW.plusDays(1))
                .targetType(targetType)
                .decisionRule(ReviewRoundDecisionRule.TOP_N)
                .selectCount(1)
                .build();
        ReflectionTestUtils.setField(round, "id", REVIEW_ROUND_ID);
        return round;
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
            ReviewRound reviewRound,
            Submission submission
    ) {
        ReviewRoundEntry entry = ReviewRoundEntry.builder()
                .reviewRound(reviewRound)
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
