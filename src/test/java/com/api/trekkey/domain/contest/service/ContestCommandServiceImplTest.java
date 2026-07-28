package com.api.trekkey.domain.contest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.api.trekkey.domain.audit.support.AdminAuditLogger;
import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStage;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.ParticipationType;
import com.api.trekkey.domain.contest.entity.StageStatus;
import com.api.trekkey.domain.contest.entity.StageType;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.contest.repository.ContestStageRepository;
import com.api.trekkey.domain.contest.support.ContestHtmlSanitizer;
import com.api.trekkey.domain.contest.web.dto.ContestCreateReq;
import com.api.trekkey.domain.contest.web.dto.ContestDetailRes;
import com.api.trekkey.domain.contest.web.dto.CriterionReq;
import com.api.trekkey.domain.contest.web.dto.StageReq;
import com.api.trekkey.domain.contest.web.dto.StageRes;
import com.api.trekkey.domain.contest.web.dto.StageStatusUpdateReq;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
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
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ContestCommandServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ContestRepository contestRepository;

    @Mock
    private ContestStageRepository contestStageRepository;

    @Mock
    private AdminAuditLogger adminAuditLogger;

    private ContestCommandServiceImpl contestCommandService;

    private Organization organization;
    private User admin;

    @BeforeEach
    void setUp() {
        contestCommandService = new ContestCommandServiceImpl(
                userRepository,
                contestRepository,
                contestStageRepository,
                new ContestHtmlSanitizer(),
                adminAuditLogger
        );

        organization = org.mockito.Mockito.mock(Organization.class);
        org.mockito.Mockito.lenient()
                .when(organization.getId())
                .thenReturn(1L);
        admin = user(10L, UserRole.ADMIN, UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("대회 생성 시 대회와 단계를 저장하고 단계 순서를 1부터 다시 부여한다")
    void createContest_savesContestAndNormalizesStageSequence() {
        given(userRepository.findById(10L))
                .willReturn(Optional.of(admin));
        stubSaveWithIds();

        ContestCreateReq req = createReq(List.of(
                stageReq(null, "제출", StageType.SUBMISSION, 8),
                stageReq(null, "참가 신청", StageType.APPLICATION, 3)
        ));

        ContestDetailRes res =
                contestCommandService.createContest(10L, req);

        assertThat(res.id()).isEqualTo("pub-100");
        assertThat(res.stages())
                .extracting(StageRes::name)
                .containsExactly("참가 신청", "제출");
        assertThat(res.stages())
                .extracting(StageRes::sequenceNo)
                .containsExactly(1, 2);
        assertThat(res.stages())
                .allSatisfy(stage ->
                        assertThat(stage.criteria()).isEmpty());
        verify(contestRepository).save(any(Contest.class));
        verify(contestStageRepository, times(2))
                .save(any(ContestStage.class));
    }

    @Test
    @DisplayName("대회 생성 시 상세 HTML에서 위험한 스크립트를 제거한다")
    void createContest_sanitizesDetailHtml() {
        given(userRepository.findById(10L))
                .willReturn(Optional.of(admin));
        stubSaveWithIds();

        ContestCreateReq req = createReqWithDetailHtml(
                "<h2>소개</h2><script>alert(1)</script>",
                List.of(stageReq(
                        null,
                        "참가 신청",
                        StageType.APPLICATION,
                        1
                ))
        );

        ContestDetailRes res =
                contestCommandService.createContest(10L, req);

        assertThat(res.detailHtml()).contains("<h2>소개</h2>");
        assertThat(res.detailHtml()).doesNotContain("script");
    }

    @Test
    @DisplayName("단계 요청에 평가 기준이 오면 심사 라운드 API 사용을 안내한다")
    void createContest_rejectsReviewCriteriaInStageRequest() {
        given(userRepository.findById(10L))
                .willReturn(Optional.of(admin));
        StageReq reviewStage = stageReq(
                null,
                "1차 심사",
                StageType.REVIEW,
                1,
                StageStatus.PREPARING,
                null,
                null,
                List.of(new CriterionReq(
                        null,
                        "creativity",
                        "창의성",
                        30,
                        1
                ))
        );

        assertThatThrownBy(() -> contestCommandService.createContest(
                10L,
                createReq(List.of(reviewStage))
        ))
                .isInstanceOf(CustomException.class)
                .extracting(error ->
                        ((CustomException) error).getBaseResponseCode())
                .isEqualTo(
                        ReviewErrorResponseCode.REVIEW_ROUND_REQUIRED);
        verify(contestRepository, never()).save(any(Contest.class));
    }

    @ParameterizedTest(name = "{0} 단계는 심사 라운드 API로 생성한다")
    @EnumSource(
            value = StageType.class,
            names = {"REVIEW", "PRESENTATION"}
    )
    @DisplayName("평가 기준이 없어도 심사·발표 단계는 일반 단계로 생성할 수 없다")
    void createContest_requiresReviewRoundApiForReviewStage(
            StageType stageType
    ) {
        given(userRepository.findById(10L))
                .willReturn(Optional.of(admin));
        StageReq reviewStage = stageReq(
                null,
                "평가",
                stageType,
                1
        );

        assertThatThrownBy(() -> contestCommandService.createContest(
                10L,
                createReq(List.of(reviewStage))
        ))
                .isInstanceOf(CustomException.class)
                .extracting(error ->
                        ((CustomException) error).getBaseResponseCode())
                .isEqualTo(
                        ReviewErrorResponseCode.REVIEW_ROUND_REQUIRED);
        verify(contestRepository, never()).save(any(Contest.class));
    }

    @Test
    @DisplayName("대회에는 제출 단계를 두 개 이상 설정할 수 없다")
    void createContest_rejectsDuplicateSubmissionStages() {
        given(userRepository.findById(10L))
                .willReturn(Optional.of(admin));
        ContestCreateReq req = createReq(List.of(
                stageReq(null, "1차 제출", StageType.SUBMISSION, 1),
                stageReq(null, "최종 제출", StageType.SUBMISSION, 2)
        ));

        assertThatThrownBy(() ->
                contestCommandService.createContest(10L, req))
                .isInstanceOf(CustomException.class)
                .extracting(error ->
                        ((CustomException) error).getBaseResponseCode())
                .isEqualTo(
                        ContestErrorResponseCode
                                .SUBMISSION_STAGE_DUPLICATED);
        verify(contestRepository, never()).save(any(Contest.class));
        verify(contestStageRepository, never())
                .save(any(ContestStage.class));
    }

    @Test
    @DisplayName("사용자를 찾을 수 없으면 대회를 생성할 수 없다")
    void createContest_rejectsUnknownUser() {
        given(userRepository.findById(10L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> contestCommandService.createContest(
                10L,
                createReq(List.of(stageReq(
                        null,
                        "참가 신청",
                        StageType.APPLICATION,
                        1
                )))
        ))
                .isInstanceOf(CustomException.class)
                .extracting(error ->
                        ((CustomException) error).getBaseResponseCode())
                .isEqualTo(UserErrorResponseCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("활성 사용자여도 관리자가 아니면 대회를 생성할 수 없다")
    void createContest_rejectsParticipant() {
        User participant =
                user(11L, UserRole.PARTICIPANT, UserStatus.ACTIVE);
        given(userRepository.findById(11L))
                .willReturn(Optional.of(participant));

        assertThatThrownBy(() -> contestCommandService.createContest(
                11L,
                createReq(List.of(stageReq(
                        null,
                        "참가 신청",
                        StageType.APPLICATION,
                        1
                )))
        ))
                .isInstanceOf(CustomException.class)
                .extracting(error ->
                        ((CustomException) error).getBaseResponseCode())
                .isEqualTo(UserErrorResponseCode.USER_INVALID_TOKEN);
        verify(contestRepository, never()).save(any(Contest.class));
    }

    @Test
    @DisplayName("관리자여도 비활성 상태면 대회를 생성할 수 없다")
    void createContest_rejectsInactiveAdmin() {
        User inactiveAdmin =
                user(12L, UserRole.ADMIN, UserStatus.INACTIVE);
        given(userRepository.findById(12L))
                .willReturn(Optional.of(inactiveAdmin));

        assertThatThrownBy(() -> contestCommandService.createContest(
                12L,
                createReq(List.of(stageReq(
                        null,
                        "참가 신청",
                        StageType.APPLICATION,
                        1
                )))
        ))
                .isInstanceOf(CustomException.class)
                .extracting(error ->
                        ((CustomException) error).getBaseResponseCode())
                .isEqualTo(UserErrorResponseCode.USER_INVALID_TOKEN);
        verify(contestRepository, never()).save(any(Contest.class));
    }

    @Test
    @DisplayName("다른 조직의 대회는 수정할 수 없다")
    void updateContest_rejectsOtherOrganization() {
        Organization otherOrganization =
                org.mockito.Mockito.mock(Organization.class);
        given(otherOrganization.getId()).willReturn(2L);
        Contest contest = contestBuilder(otherOrganization);
        given(userRepository.findById(10L))
                .willReturn(Optional.of(admin));
        given(contestRepository.findByPublicId("pub-1"))
                .willReturn(Optional.of(contest));

        assertThatThrownBy(() -> contestCommandService.updateContest(
                10L,
                "pub-1",
                createReq(List.of(stageReq(
                        null,
                        "참가 신청",
                        StageType.APPLICATION,
                        1
                )))
        ))
                .isInstanceOf(CustomException.class)
                .extracting(error ->
                        ((CustomException) error).getBaseResponseCode())
                .isEqualTo(ContestErrorResponseCode.CONTEST_FORBIDDEN);
        verify(contestStageRepository, never())
                .findAllForUpdateByContestIdOrderBySequenceNoAsc(any());
    }

    @Test
    @DisplayName("대회 수정 시 준비 중인 단계를 추가·수정·삭제할 수 있다")
    void updateContest_addsUpdatesAndDeletesPreparingStages() {
        Contest contest = contestBuilder(organization);
        ReflectionTestUtils.setField(contest, "id", 100L);
        ContestStage application = stageEntity(
                contest,
                201L,
                "참가 신청",
                StageType.APPLICATION,
                1,
                StageStatus.PREPARING,
                null
        );
        ContestStage removedSubmission = stageEntity(
                contest,
                202L,
                "제출",
                StageType.SUBMISSION,
                2,
                StageStatus.PREPARING,
                LocalDateTime.now().plusDays(3)
        );
        given(userRepository.findById(10L))
                .willReturn(Optional.of(admin));
        given(contestRepository.findByPublicId("pub-1"))
                .willReturn(Optional.of(contest));
        given(contestStageRepository
                .findAllForUpdateByContestIdOrderBySequenceNoAsc(100L))
                .willReturn(List.of(application, removedSubmission));
        stubStageSaveWithIds();

        ContestDetailRes res = contestCommandService.updateContest(
                10L,
                "pub-1",
                createReq(List.of(
                        stageReq(
                                201L,
                                "참가 접수",
                                StageType.APPLICATION,
                                1
                        ),
                        stageReq(
                                null,
                                "시상",
                                StageType.AWARD,
                                2
                        )
                ))
        );

        assertThat(application.getName()).isEqualTo("참가 접수");
        assertThat(res.stages())
                .extracting(StageRes::name)
                .containsExactly("참가 접수", "시상");
        assertThat(res.stages().get(1).id()).isEqualTo(300L);
        verify(contestStageRepository)
                .deleteAll(List.of(removedSubmission));
        verify(contestStageRepository, atLeastOnce()).flush();
    }

    @Test
    @DisplayName("진행 중인 단계의 설정은 대회 수정 API에서 바꿀 수 없다")
    void updateContest_rejectsConfigurationChangeOnOpenStage() {
        Contest contest = contestBuilder(organization);
        ReflectionTestUtils.setField(contest, "id", 100L);
        ContestStage openStage = stageEntity(
                contest,
                201L,
                "참가 신청",
                StageType.APPLICATION,
                1,
                StageStatus.OPEN,
                null
        );
        given(userRepository.findById(10L))
                .willReturn(Optional.of(admin));
        given(contestRepository.findByPublicId("pub-1"))
                .willReturn(Optional.of(contest));
        given(contestStageRepository
                .findAllForUpdateByContestIdOrderBySequenceNoAsc(100L))
                .willReturn(List.of(openStage));

        assertThatThrownBy(() -> contestCommandService.updateContest(
                10L,
                "pub-1",
                createReq(List.of(stageReq(
                        201L,
                        "참가 신청 변경",
                        StageType.APPLICATION,
                        1,
                        StageStatus.OPEN,
                        null,
                        null,
                        null
                )))
        ))
                .isInstanceOf(CustomException.class)
                .extracting(error ->
                        ((CustomException) error).getBaseResponseCode())
                .isEqualTo(
                        ContestErrorResponseCode
                                .STAGE_CONFIGURATION_LOCKED);
        assertThat(openStage.getName()).isEqualTo("참가 신청");
    }

    @Test
    @DisplayName("진행 중인 단계는 대회 수정 요청에서 제거할 수 없다")
    void updateContest_rejectsDeletingOpenStage() {
        Contest contest = contestBuilder(organization);
        ReflectionTestUtils.setField(contest, "id", 100L);
        ContestStage openStage = stageEntity(
                contest,
                201L,
                "참가 신청",
                StageType.APPLICATION,
                1,
                StageStatus.OPEN,
                null
        );
        given(userRepository.findById(10L))
                .willReturn(Optional.of(admin));
        given(contestRepository.findByPublicId("pub-1"))
                .willReturn(Optional.of(contest));
        given(contestStageRepository
                .findAllForUpdateByContestIdOrderBySequenceNoAsc(100L))
                .willReturn(List.of(openStage));

        assertThatThrownBy(() -> contestCommandService.updateContest(
                10L,
                "pub-1",
                createReq(List.of())
        ))
                .isInstanceOf(CustomException.class)
                .extracting(error ->
                        ((CustomException) error).getBaseResponseCode())
                .isEqualTo(
                        ContestErrorResponseCode
                                .STAGE_CONFIGURATION_LOCKED);
        verify(contestStageRepository, never()).deleteAll(anyList());
    }

    @Test
    @DisplayName("준비 중인 단계 순서를 맞바꿀 때 임시 순서를 먼저 반영한다")
    void updateContest_flushesTemporarySequencesBeforeSwap() {
        Contest contest = contestBuilder(organization);
        ReflectionTestUtils.setField(contest, "id", 100L);
        ContestStage application = stageEntity(
                contest,
                201L,
                "참가 신청",
                StageType.APPLICATION,
                1,
                StageStatus.PREPARING,
                null
        );
        ContestStage submission = stageEntity(
                contest,
                202L,
                "제출",
                StageType.SUBMISSION,
                2,
                StageStatus.PREPARING,
                LocalDateTime.now().plusDays(3)
        );
        given(userRepository.findById(10L))
                .willReturn(Optional.of(admin));
        given(contestRepository.findByPublicId("pub-1"))
                .willReturn(Optional.of(contest));
        given(contestStageRepository
                .findAllForUpdateByContestIdOrderBySequenceNoAsc(100L))
                .willReturn(List.of(application, submission));

        ContestDetailRes res = contestCommandService.updateContest(
                10L,
                "pub-1",
                createReq(List.of(
                        stageReq(
                                201L,
                                "참가 신청",
                                StageType.APPLICATION,
                                2
                        ),
                        stageReq(
                                202L,
                                "제출",
                                StageType.SUBMISSION,
                                1,
                                StageStatus.PREPARING,
                                null,
                                submission.getEndsAt(),
                                null
                        )
                ))
        );

        assertThat(res.stages())
                .extracting(StageRes::id)
                .containsExactly(202L, 201L);
        assertThat(submission.getSequenceNo()).isEqualTo(1);
        assertThat(application.getSequenceNo()).isEqualTo(2);
        verify(contestStageRepository).flush();
    }

    @Test
    @DisplayName("수정 요청에서 같은 단계 ID를 두 번 사용할 수 없다")
    void updateContest_rejectsDuplicateStageIds() {
        Contest contest = contestBuilder(organization);
        ReflectionTestUtils.setField(contest, "id", 100L);
        ContestStage application = stageEntity(
                contest,
                201L,
                "참가 신청",
                StageType.APPLICATION,
                1,
                StageStatus.PREPARING,
                null
        );
        given(userRepository.findById(10L))
                .willReturn(Optional.of(admin));
        given(contestRepository.findByPublicId("pub-1"))
                .willReturn(Optional.of(contest));
        given(contestStageRepository
                .findAllForUpdateByContestIdOrderBySequenceNoAsc(100L))
                .willReturn(List.of(application));

        assertThatThrownBy(() -> contestCommandService.updateContest(
                10L,
                "pub-1",
                createReq(List.of(
                        stageReq(
                                201L,
                                "참가 신청",
                                StageType.APPLICATION,
                                1
                        ),
                        stageReq(
                                201L,
                                "시상",
                                StageType.AWARD,
                                2
                        )
                ))
        ))
                .isInstanceOf(CustomException.class)
                .extracting(error ->
                        ((CustomException) error).getBaseResponseCode())
                .isEqualTo(ContestErrorResponseCode.STAGE_DUPLICATED);
    }

    @Test
    @DisplayName("일반 단계는 준비 상태에서 진행 상태로 변경할 수 있다")
    void updateStageStatus_opensGenericStage() {
        Contest contest = contestBuilder(organization);
        ContestStage stage = stageEntity(
                contest,
                201L,
                "참가 신청",
                StageType.APPLICATION,
                1,
                StageStatus.PREPARING,
                null
        );
        stubStageStatusLookup(201L, stage);

        StageRes res = contestCommandService.updateStageStatus(
                10L,
                201L,
                new StageStatusUpdateReq(StageStatus.OPEN)
        );

        assertThat(res.status()).isEqualTo(StageStatus.OPEN);
        verify(adminAuditLogger).log(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("마감 시각이 없는 제출 단계는 시작할 수 없다")
    void updateStageStatus_rejectsInvalidSubmissionConfiguration() {
        Contest contest = contestBuilder(organization);
        ContestStage stage = stageEntity(
                contest,
                201L,
                "제출",
                StageType.SUBMISSION,
                1,
                StageStatus.PREPARING,
                null
        );
        stubStageStatusLookup(201L, stage);

        assertThatThrownBy(() ->
                contestCommandService.updateStageStatus(
                        10L,
                        201L,
                        new StageStatusUpdateReq(StageStatus.OPEN)
                ))
                .isInstanceOf(CustomException.class)
                .extracting(error ->
                        ((CustomException) error).getBaseResponseCode())
                .isEqualTo(
                        ContestErrorResponseCode
                                .STAGE_CONFIGURATION_INVALID);
        assertThat(stage.getStatus())
                .isEqualTo(StageStatus.PREPARING);
    }

    @Test
    @DisplayName("일반 단계도 중간 상태를 건너뛰어 완료할 수 없다")
    void updateStageStatus_rejectsSkippedTransition() {
        Contest contest = contestBuilder(organization);
        ContestStage stage = stageEntity(
                contest,
                201L,
                "참가 신청",
                StageType.APPLICATION,
                1,
                StageStatus.PREPARING,
                null
        );
        stubStageStatusLookup(201L, stage);

        assertThatThrownBy(() ->
                contestCommandService.updateStageStatus(
                        10L,
                        201L,
                        new StageStatusUpdateReq(StageStatus.COMPLETED)
                ))
                .isInstanceOf(CustomException.class)
                .extracting(error ->
                        ((CustomException) error).getBaseResponseCode())
                .isEqualTo(
                        ContestErrorResponseCode
                                .INVALID_STAGE_STATUS_TRANSITION);
        assertThat(stage.getStatus())
                .isEqualTo(StageStatus.PREPARING);
    }

    @ParameterizedTest(name = "{0} 단계 상태는 심사 라운드 API로 변경한다")
    @EnumSource(
            value = StageType.class,
            names = {"REVIEW", "PRESENTATION"}
    )
    @DisplayName("심사·발표 단계의 상태는 일반 단계 API에서 변경할 수 없다")
    void updateStageStatus_requiresReviewRoundApiForReviewStage(
            StageType stageType
    ) {
        Contest contest = contestBuilder(organization);
        ContestStage stage = stageEntity(
                contest,
                201L,
                "평가",
                stageType,
                1,
                StageStatus.PREPARING,
                null
        );
        stubStageStatusLookup(201L, stage);

        assertThatThrownBy(() ->
                contestCommandService.updateStageStatus(
                        10L,
                        201L,
                        new StageStatusUpdateReq(StageStatus.OPEN)
                ))
                .isInstanceOf(CustomException.class)
                .extracting(error ->
                        ((CustomException) error).getBaseResponseCode())
                .isEqualTo(
                        ReviewErrorResponseCode.REVIEW_ROUND_REQUIRED);
        assertThat(stage.getStatus())
                .isEqualTo(StageStatus.PREPARING);
    }

    @Test
    @DisplayName("다른 조직의 단계는 잠그기 전에 상태 변경을 거부한다")
    void updateStageStatus_rejectsOtherOrganizationBeforeLock() {
        given(userRepository.findById(10L))
                .willReturn(Optional.of(admin));
        given(contestStageRepository.findOrganizationIdById(201L))
                .willReturn(Optional.of(2L));

        assertThatThrownBy(() ->
                contestCommandService.updateStageStatus(
                        10L,
                        201L,
                        new StageStatusUpdateReq(StageStatus.OPEN)
                ))
                .isInstanceOf(CustomException.class)
                .extracting(error ->
                        ((CustomException) error).getBaseResponseCode())
                .isEqualTo(ContestErrorResponseCode.CONTEST_FORBIDDEN);
        verify(contestStageRepository, never())
                .findByIdForUpdate(201L);
    }

    @Test
    @DisplayName("존재하지 않는 단계의 상태를 변경할 수 없다")
    void updateStageStatus_rejectsUnknownStage() {
        given(userRepository.findById(10L))
                .willReturn(Optional.of(admin));
        given(contestStageRepository.findOrganizationIdById(999L))
                .willReturn(Optional.of(1L));
        given(contestStageRepository.findByIdForUpdate(999L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                contestCommandService.updateStageStatus(
                        10L,
                        999L,
                        new StageStatusUpdateReq(StageStatus.OPEN)
                ))
                .isInstanceOf(CustomException.class)
                .extracting(error ->
                        ((CustomException) error).getBaseResponseCode())
                .isEqualTo(ContestErrorResponseCode.STAGE_NOT_FOUND);
    }

    private void stubSaveWithIds() {
        AtomicLong contestIds = new AtomicLong(100L);
        given(contestRepository.save(any(Contest.class)))
                .willAnswer(invocation -> {
                    Contest contest = invocation.getArgument(0);
                    ReflectionTestUtils.setField(
                            contest,
                            "id",
                            contestIds.getAndIncrement()
                    );
                    ReflectionTestUtils.setField(
                            contest,
                            "publicId",
                            "pub-" + contest.getId()
                    );
                    return contest;
                });
        stubStageSaveWithIds();
    }

    private void stubStageSaveWithIds() {
        AtomicLong stageIds = new AtomicLong(300L);
        given(contestStageRepository.save(any(ContestStage.class)))
                .willAnswer(invocation -> {
                    ContestStage stage = invocation.getArgument(0);
                    ReflectionTestUtils.setField(
                            stage,
                            "id",
                            stageIds.getAndIncrement()
                    );
                    return stage;
                });
    }

    private void stubStageStatusLookup(
            Long stageId,
            ContestStage stage
    ) {
        given(userRepository.findById(10L))
                .willReturn(Optional.of(admin));
        given(contestStageRepository.findOrganizationIdById(stageId))
                .willReturn(Optional.of(1L));
        given(contestStageRepository.findByIdForUpdate(stageId))
                .willReturn(Optional.of(stage));
    }

    private User user(
            Long id,
            UserRole role,
            UserStatus status
    ) {
        User user = User.builder()
                .organization(organization)
                .name("사용자")
                .email("user-" + id + "@hansung.ac.kr")
                .password("encoded")
                .role(role)
                .memberType(MemberType.STAFF)
                .status(status)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private ContestCreateReq createReq(List<StageReq> stages) {
        return createReqWithDetailHtml("<p>본문</p>", stages);
    }

    private ContestCreateReq createReqWithDetailHtml(
            String detailHtml,
            List<StageReq> stages
    ) {
        return new ContestCreateReq(
                "2026 AI 공모전",
                "교무처",
                ContestStatus.APPLICATION_OPEN,
                ParticipationType.BOTH,
                3,
                null,
                "AI 공모전",
                "재학생",
                "온라인 접수",
                "상장 수여",
                "AI,공모전",
                detailHtml,
                stages
        );
    }

    private StageReq stageReq(
            Long id,
            String name,
            StageType stageType,
            int sequenceNo
    ) {
        return stageReq(
                id,
                name,
                stageType,
                sequenceNo,
                StageStatus.PREPARING,
                null,
                null,
                null
        );
    }

    private StageReq stageReq(
            Long id,
            String name,
            StageType stageType,
            int sequenceNo,
            StageStatus status,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            List<CriterionReq> criteria
    ) {
        return new StageReq(
                id,
                name,
                stageType,
                sequenceNo,
                status,
                startsAt,
                endsAt,
                null,
                null,
                null,
                null,
                criteria
        );
    }

    private Contest contestBuilder(
            Organization contestOrganization
    ) {
        return Contest.builder()
                .publicId("pub-1")
                .organization(contestOrganization)
                .ownerUser(admin)
                .title("기존 대회")
                .department("교무처")
                .status(ContestStatus.PREPARING)
                .participationType(ParticipationType.BOTH)
                .awardCount(3)
                .summary("요약")
                .target("재학생")
                .applicationMethod("온라인")
                .benefits("상장")
                .detailHtml("<p>본문</p>")
                .build();
    }

    private ContestStage stageEntity(
            Contest contest,
            Long id,
            String name,
            StageType stageType,
            int sequenceNo,
            StageStatus status,
            LocalDateTime endsAt
    ) {
        ContestStage stage = ContestStage.builder()
                .contest(contest)
                .name(name)
                .stageType(stageType)
                .sequenceNo(sequenceNo)
                .status(status)
                .endsAt(endsAt)
                .build();
        ReflectionTestUtils.setField(stage, "id", id);
        return stage;
    }
}
