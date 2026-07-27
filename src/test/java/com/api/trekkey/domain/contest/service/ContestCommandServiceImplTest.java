package com.api.trekkey.domain.contest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import com.api.trekkey.domain.contest.support.ContestHtmlSanitizer;
import com.api.trekkey.domain.contest.web.dto.ContestCreateReq;
import com.api.trekkey.domain.contest.web.dto.ContestDetailRes;
import com.api.trekkey.domain.contest.web.dto.CriterionReq;
import com.api.trekkey.domain.contest.web.dto.CriterionRes;
import com.api.trekkey.domain.contest.web.dto.StageReq;
import com.api.trekkey.domain.contest.web.dto.StageRes;
import com.api.trekkey.domain.contest.web.dto.StageStatusUpdateReq;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.entity.ReviewRoundEntry;
import com.api.trekkey.domain.review.entity.ReviewRoundEntryStatus;
import com.api.trekkey.domain.review.repository.ReviewRoundEntryRepository;
import com.api.trekkey.domain.user.entity.MemberType;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private ReviewCriterionRepository reviewCriterionRepository;

    @Mock
    private ReviewRoundEntryRepository reviewRoundEntryRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private AdminAuditLogger adminAuditLogger;

    private ContestCommandServiceImpl contestCommandService;

    private Organization organization;
    private User admin;

    @BeforeEach
    void setUp() {
        // sanitizer는 순수 컴포넌트이므로 실객체로 검증한다
        contestCommandService = new ContestCommandServiceImpl(
                userRepository,
                contestRepository,
                contestStageRepository,
                reviewCriterionRepository,
                reviewRoundEntryRepository,
                entityManager,
                new ContestHtmlSanitizer(),
                adminAuditLogger);

        organization = org.mockito.Mockito.mock(Organization.class);
        // 일부 테스트는 조직 검증 전에 예외로 종료되므로 strict stubbing에서 제외한다
        org.mockito.Mockito.lenient().when(organization.getId()).thenReturn(1L);
        org.mockito.Mockito.lenient()
                .when(contestStageRepository.findOrganizationIdById(any(Long.class)))
                .thenReturn(Optional.of(1L));
        org.mockito.Mockito.lenient()
                .when(reviewRoundEntryRepository
                        .findAllForUpdateByReviewStageIdOrderByIdAsc(
                                any(Long.class)))
                .thenReturn(List.of(org.mockito.Mockito.mock(
                        ReviewRoundEntry.class)));

        admin = User.builder()
                .organization(organization)
                .name("김교수")
                .email("prof@hansung.ac.kr")
                .password("encoded")
                .role(UserRole.ADMIN)
                .memberType(MemberType.STAFF)
                .status(UserStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(admin, "id", 10L);
    }

    @Test
    @DisplayName("대회 생성 시 단계가 sequenceNo 순으로 1..n 재부여되고 기준 코드가 자동 생성된다")
    void createContest_reordersStagesAndGeneratesCriterionCode() {
        given(userRepository.findById(10L)).willReturn(Optional.of(admin));
        stubSaveWithIds();

        ContestCreateReq req = createReq(List.of(
                stageReq(null, "최종 심사", StageType.REVIEW, 5,
                        List.of(new CriterionReq(null, " ", "창의성", 30, null))),
                stageReq(null, "참가 신청", StageType.APPLICATION, 2, null)));

        ContestDetailRes res = contestCommandService.createContest(10L, req);

        assertThat(res.stages()).hasSize(2);
        assertThat(res.stages().get(0).name()).isEqualTo("참가 신청");
        assertThat(res.stages().get(0).sequenceNo()).isEqualTo(1);
        assertThat(res.stages().get(1).name()).isEqualTo("최종 심사");
        assertThat(res.stages().get(1).sequenceNo()).isEqualTo(2);
        assertThat(res.stages().get(1).criteria().get(0).code()).isEqualTo("criterion-1");
    }

    @Test
    @DisplayName("대회 생성 시 detailHtml의 script 태그가 제거된다")
    void createContest_sanitizesDetailHtml() {
        given(userRepository.findById(10L)).willReturn(Optional.of(admin));
        stubSaveWithIds();

        ContestCreateReq req = createReqWithDetailHtml(
                "<h2>소개</h2><script>alert(1)</script>",
                List.of(stageReq(null, "참가 신청", StageType.APPLICATION, 1, null)));

        ContestDetailRes res = contestCommandService.createContest(10L, req);

        assertThat(res.detailHtml()).contains("<h2>소개</h2>");
        assertThat(res.detailHtml()).doesNotContain("script");
    }

    @Test
    @DisplayName("대회에는 제출 단계를 두 개 이상 설정할 수 없다")
    void createContest_rejectsDuplicateSubmissionStages() {
        given(userRepository.findById(10L)).willReturn(Optional.of(admin));
        ContestCreateReq req = createReq(List.of(
                stageReq(null, "1차 제출", StageType.SUBMISSION, 1, null),
                stageReq(null, "최종 제출", StageType.SUBMISSION, 2, null)
        ));

        assertThatThrownBy(() ->
                contestCommandService.createContest(10L, req))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(
                        ContestErrorResponseCode.SUBMISSION_STAGE_DUPLICATED);

        verify(contestRepository, never()).save(any(Contest.class));
        verify(contestStageRepository, never()).save(any(ContestStage.class));
    }

    @Test
    @DisplayName("사용자를 찾을 수 없으면 USER_NOT_FOUND 예외가 발생한다")
    void createContest_throwsWhenUserNotFound() {
        given(userRepository.findById(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> contestCommandService.createContest(10L,
                createReq(List.of(stageReq(null, "참가 신청", StageType.APPLICATION, 1, null)))))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(UserErrorResponseCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("타 조직 대회 수정 시 CONTEST_FORBIDDEN 예외가 발생한다")
    void updateContest_throwsWhenOtherOrganization() {
        Organization otherOrganization = org.mockito.Mockito.mock(Organization.class);
        given(otherOrganization.getId()).willReturn(2L);
        Contest contest = contestBuilder(otherOrganization);

        given(userRepository.findById(10L)).willReturn(Optional.of(admin));
        given(contestRepository.findByPublicId("pub-1")).willReturn(Optional.of(contest));

        assertThatThrownBy(() -> contestCommandService.updateContest(10L, "pub-1",
                createReq(List.of(stageReq(null, "참가 신청", StageType.APPLICATION, 1, null)))))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ContestErrorResponseCode.CONTEST_FORBIDDEN);
    }

    @Test
    @DisplayName("요청에 없는 기존 단계는 수정 시 삭제된다")
    void updateContest_deletesStagesNotInRequest() {
        Contest contest = contestBuilder(organization);
        ReflectionTestUtils.setField(contest, "id", 100L);
        ContestStage keptStage = stageEntity(contest, 201L, "참가 신청", StageType.APPLICATION, 1);
        ContestStage removedStage = stageEntity(contest, 202L, "제출", StageType.SUBMISSION, 2);

        given(userRepository.findById(10L)).willReturn(Optional.of(admin));
        given(contestRepository.findByPublicId("pub-1")).willReturn(Optional.of(contest));
        given(contestStageRepository.findAllForUpdateByContestIdOrderBySequenceNoAsc(100L))
                .willReturn(List.of(keptStage, removedStage));

        ContestDetailRes res = contestCommandService.updateContest(10L, "pub-1",
                createReq(List.of(stageReq(201L, "참가 접수(변경)", StageType.APPLICATION, 1, null))));

        verify(contestStageRepository).deleteAll(List.of(removedStage));
        assertThat(res.stages()).hasSize(1);
        assertThat(res.stages().get(0).name()).isEqualTo("참가 접수(변경)");
    }

    @Test
    @DisplayName("존재하지 않는 단계 id가 포함되면 STAGE_NOT_FOUND 예외가 발생한다")
    void updateContest_throwsWhenStageIdUnknown() {
        Contest contest = contestBuilder(organization);
        ReflectionTestUtils.setField(contest, "id", 100L);

        given(userRepository.findById(10L)).willReturn(Optional.of(admin));
        given(contestRepository.findByPublicId("pub-1")).willReturn(Optional.of(contest));
        given(contestStageRepository.findAllForUpdateByContestIdOrderBySequenceNoAsc(100L))
                .willReturn(List.of());

        assertThatThrownBy(() -> contestCommandService.updateContest(10L, "pub-1",
                createReq(List.of(stageReq(999L, "참가 신청", StageType.APPLICATION, 1, null)))))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ContestErrorResponseCode.STAGE_NOT_FOUND);
    }

    @Test
    @DisplayName("준비 중인 심사 단계 수정 시 기존 기준 ID를 유지하고 누락 기준은 비활성화한다")
    void updateContest_preservesCriterionIdAndDeactivatesOmittedCriterion() {
        Contest contest = contestBuilder(organization);
        ReflectionTestUtils.setField(contest, "id", 100L);
        ContestStage stage = stageEntity(contest, 201L, "심사", StageType.REVIEW, 1);
        ReviewCriterion creativity =
                criterionEntity(stage, 301L, "creativity", "창의성", 30, 1, true);
        ReviewCriterion completeness =
                criterionEntity(stage, 302L, "completeness", "완성도", 30, 2, true);

        given(userRepository.findById(10L)).willReturn(Optional.of(admin));
        given(contestRepository.findByPublicId("pub-1")).willReturn(Optional.of(contest));
        given(contestStageRepository.findAllForUpdateByContestIdOrderBySequenceNoAsc(100L))
                .willReturn(List.of(stage));
        given(reviewCriterionRepository.findAllForUpdateByContestStageIdInOrderBySortOrderAsc(
                List.of(201L)))
                .willReturn(List.of(creativity, completeness));
        stubCriterionSaveWithIds();

        ContestDetailRes res = contestCommandService.updateContest(
                10L,
                "pub-1",
                createReq(List.of(stageReq(
                        201L,
                        "심사",
                        StageType.REVIEW,
                        1,
                        List.of(
                                new CriterionReq(301L, "creativity", "창의성 개선", 40, 1),
                                new CriterionReq(null, "impact", "파급력", 20, 2)
                        )
                )))
        );

        assertThat(res.stages().get(0).criteria())
                .extracting(CriterionRes::code)
                .containsExactly("creativity", "impact");
        assertThat(res.stages().get(0).criteria().get(0).id()).isEqualTo(301L);
        assertThat(creativity.getLabel()).isEqualTo("창의성 개선");
        assertThat(creativity.getMaxScore()).isEqualTo(40);
        assertThat(completeness.isActive()).isFalse();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReviewCriterion>> captor = ArgumentCaptor.forClass(List.class);
        verify(reviewCriterionRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(3);
        verify(reviewCriterionRepository, never()).deleteByContestStageIdIn(anyList());
    }

    @Test
    @DisplayName("기준 ID가 없어도 같은 코드의 비활성 기준을 재사용하고 활성화한다")
    void updateContest_reactivatesCriterionByCodeWhenIdIsMissing() {
        Contest contest = contestBuilder(organization);
        ReflectionTestUtils.setField(contest, "id", 100L);
        ContestStage stage = stageEntity(contest, 201L, "심사", StageType.REVIEW, 1);
        ReviewCriterion criterion =
                criterionEntity(stage, 301L, "creativity", "창의성", 30, 1, false);

        given(userRepository.findById(10L)).willReturn(Optional.of(admin));
        given(contestRepository.findByPublicId("pub-1")).willReturn(Optional.of(contest));
        given(contestStageRepository.findAllForUpdateByContestIdOrderBySequenceNoAsc(100L))
                .willReturn(List.of(stage));
        given(reviewCriterionRepository.findAllForUpdateByContestStageIdInOrderBySortOrderAsc(
                List.of(201L)))
                .willReturn(List.of(criterion));
        stubCriterionSaveWithIds();

        ContestDetailRes res = contestCommandService.updateContest(
                10L,
                "pub-1",
                createReq(List.of(stageReq(
                        201L,
                        "심사",
                        StageType.REVIEW,
                        1,
                        List.of(new CriterionReq(null, "creativity", "창의성", 35, 1))
                )))
        );

        assertThat(res.stages().get(0).criteria()).hasSize(1);
        assertThat(res.stages().get(0).criteria().get(0).id()).isEqualTo(301L);
        assertThat(criterion.isActive()).isTrue();
        assertThat(criterion.getMaxScore()).isEqualTo(35);
    }

    @Test
    @DisplayName("저장된 평가 기준의 코드는 변경할 수 없다")
    void updateContest_rejectsChangingCriterionCode() {
        Contest contest = contestBuilder(organization);
        ReflectionTestUtils.setField(contest, "id", 100L);
        ContestStage stage = stageEntity(contest, 201L, "심사", StageType.REVIEW, 1);
        configureReviewStageForOpening(stage, StagePassRule.FINAL, null, null);
        ReviewCriterion criterion =
                criterionEntity(stage, 301L, "creativity", "창의성", 30, 1, true);

        given(userRepository.findById(10L)).willReturn(Optional.of(admin));
        given(contestRepository.findByPublicId("pub-1")).willReturn(Optional.of(contest));
        given(contestStageRepository.findAllForUpdateByContestIdOrderBySequenceNoAsc(100L))
                .willReturn(List.of(stage));
        given(reviewCriterionRepository.findAllForUpdateByContestStageIdInOrderBySortOrderAsc(
                List.of(201L)))
                .willReturn(List.of(criterion));

        assertThatThrownBy(() -> contestCommandService.updateContest(
                10L,
                "pub-1",
                createReq(List.of(stageReq(
                        201L,
                        "심사",
                        StageType.REVIEW,
                        1,
                        List.of(new CriterionReq(301L, "originality", "창의성", 30, 1))
                )))
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ContestErrorResponseCode.REVIEW_CRITERION_CODE_IMMUTABLE);
    }

    @Test
    @DisplayName("심사·발표가 아닌 단계에 평가 기준을 설정할 수 없다")
    void createContest_rejectsCriterionOnNonReviewStage() {
        given(userRepository.findById(10L)).willReturn(Optional.of(admin));

        ContestCreateReq req = createReq(List.of(stageReq(
                null,
                "참가 신청",
                StageType.APPLICATION,
                1,
                List.of(new CriterionReq(null, "fit", "적합성", 10, 1))
        )));

        assertThatThrownBy(() -> contestCommandService.createContest(10L, req))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ContestErrorResponseCode.REVIEW_CRITERION_NOT_ALLOWED);
        verify(contestRepository, never()).save(any(Contest.class));
    }

    @Test
    @DisplayName("한 심사 단계 안에서 대소문자만 다른 중복 기준 코드를 허용하지 않는다")
    void createContest_rejectsDuplicateCriterionCode() {
        given(userRepository.findById(10L)).willReturn(Optional.of(admin));

        ContestCreateReq req = createReq(List.of(stageReq(
                null,
                "심사",
                StageType.REVIEW,
                1,
                List.of(
                        new CriterionReq(null, "creativity", "창의성", 30, 1),
                        new CriterionReq(null, " Creativity ", "독창성", 30, 2)
                )
        )));

        assertThatThrownBy(() -> contestCommandService.createContest(10L, req))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ContestErrorResponseCode.REVIEW_CRITERION_DUPLICATED);
    }

    @Test
    @DisplayName("새 평가 기준의 배점은 1점 이상이어야 한다")
    void createContest_rejectsZeroMaxScoreForNewCriterion() {
        given(userRepository.findById(10L)).willReturn(Optional.of(admin));

        ContestCreateReq req = createReq(List.of(stageReq(
                null,
                "심사",
                StageType.REVIEW,
                1,
                List.of(new CriterionReq(null, "creativity", "창의성", 0, 1))
        )));

        assertThatThrownBy(() -> contestCommandService.createContest(10L, req))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ContestErrorResponseCode.REVIEW_CRITERION_INVALID);
    }

    @Test
    @DisplayName("평가 기준 코드는 ASCII 영문·숫자·하이픈·밑줄만 허용한다")
    void createContest_rejectsNonAsciiCriterionCode() {
        given(userRepository.findById(10L)).willReturn(Optional.of(admin));

        ContestCreateReq req = createReq(List.of(stageReq(
                null,
                "심사",
                StageType.REVIEW,
                1,
                List.of(new CriterionReq(null, "창의성", "창의성", 30, 1))
        )));

        assertThatThrownBy(() -> contestCommandService.createContest(10L, req))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ContestErrorResponseCode.REVIEW_CRITERION_INVALID);
    }

    @Test
    @DisplayName("심사가 시작된 단계의 설정은 대회 수정 API에서 변경할 수 없다")
    void updateContest_rejectsConfigurationChangeOnOpenStage() {
        Contest contest = contestBuilder(organization);
        ReflectionTestUtils.setField(contest, "id", 100L);
        ContestStage stage =
                stageEntity(contest, 201L, "심사", StageType.REVIEW, 1, StageStatus.OPEN);
        ReviewCriterion criterion =
                criterionEntity(stage, 301L, "creativity", "창의성", 30, 1, true);

        given(userRepository.findById(10L)).willReturn(Optional.of(admin));
        given(contestRepository.findByPublicId("pub-1")).willReturn(Optional.of(contest));
        given(contestStageRepository.findAllForUpdateByContestIdOrderBySequenceNoAsc(100L))
                .willReturn(List.of(stage));
        given(reviewCriterionRepository.findAllForUpdateByContestStageIdInOrderBySortOrderAsc(
                List.of(201L)))
                .willReturn(List.of(criterion));

        StageReq changedStage = new StageReq(
                201L,
                "심사(변경)",
                StageType.REVIEW,
                1,
                StageStatus.OPEN,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(new CriterionReq(301L, "creativity", "창의성", 30, 1))
        );

        assertThatThrownBy(() -> contestCommandService.updateContest(
                10L,
                "pub-1",
                createReq(List.of(changedStage))
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ContestErrorResponseCode.STAGE_CONFIGURATION_LOCKED);
    }

    @Test
    @DisplayName("심사 대상이 준비된 단계는 아직 PREPARING이어도 설정을 변경할 수 없다")
    void updateContest_rejectsConfigurationChangeAfterEntriesPrepared() {
        Contest contest = contestBuilder(organization);
        ReflectionTestUtils.setField(contest, "id", 100L);
        ContestStage stage =
                stageEntity(contest, 201L, "심사", StageType.REVIEW, 1);
        ReviewCriterion criterion =
                criterionEntity(stage, 301L, "creativity", "창의성", 30, 1, true);

        given(userRepository.findById(10L)).willReturn(Optional.of(admin));
        given(contestRepository.findByPublicId("pub-1"))
                .willReturn(Optional.of(contest));
        given(contestStageRepository
                .findAllForUpdateByContestIdOrderBySequenceNoAsc(100L))
                .willReturn(List.of(stage));
        given(reviewCriterionRepository
                .findAllForUpdateByContestStageIdInOrderBySortOrderAsc(
                        List.of(201L)))
                .willReturn(List.of(criterion));
        given(reviewRoundEntryRepository.findStagesWithEntriesForShare(
                List.of(201L))).willReturn(List.of(stage));

        StageReq changedStage = stageReq(
                201L,
                "심사(변경)",
                StageType.REVIEW,
                1,
                List.of(new CriterionReq(
                        301L,
                        "creativity",
                        "창의성",
                        30,
                        1
                ))
        );

        assertThatThrownBy(() -> contestCommandService.updateContest(
                10L,
                "pub-1",
                createReq(List.of(changedStage))
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(
                        ContestErrorResponseCode.STAGE_CONFIGURATION_LOCKED);
    }

    @Test
    @DisplayName("심사 대상이 준비된 단계는 아직 PREPARING이어도 삭제할 수 없다")
    void updateContest_rejectsDeletingStageAfterEntriesPrepared() {
        Contest contest = contestBuilder(organization);
        ReflectionTestUtils.setField(contest, "id", 100L);
        ContestStage stage =
                stageEntity(contest, 201L, "심사", StageType.REVIEW, 1);

        given(userRepository.findById(10L)).willReturn(Optional.of(admin));
        given(contestRepository.findByPublicId("pub-1"))
                .willReturn(Optional.of(contest));
        given(contestStageRepository
                .findAllForUpdateByContestIdOrderBySequenceNoAsc(100L))
                .willReturn(List.of(stage));
        given(reviewRoundEntryRepository.findStagesWithEntriesForShare(
                List.of(201L))).willReturn(List.of(stage));

        assertThatThrownBy(() -> contestCommandService.updateContest(
                10L,
                "pub-1",
                createReq(List.of())
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(
                        ContestErrorResponseCode.STAGE_CONFIGURATION_LOCKED);

        verify(contestStageRepository, never()).deleteAll(anyList());
    }

    @Test
    @DisplayName("심사가 시작된 단계의 평가 기준 배점은 변경할 수 없다")
    void updateContest_rejectsCriterionChangeOnOpenStage() {
        Contest contest = contestBuilder(organization);
        ReflectionTestUtils.setField(contest, "id", 100L);
        ContestStage stage =
                stageEntity(contest, 201L, "심사", StageType.REVIEW, 1, StageStatus.OPEN);
        ReviewCriterion criterion =
                criterionEntity(stage, 301L, "creativity", "창의성", 30, 1, true);

        given(userRepository.findById(10L)).willReturn(Optional.of(admin));
        given(contestRepository.findByPublicId("pub-1")).willReturn(Optional.of(contest));
        given(contestStageRepository.findAllForUpdateByContestIdOrderBySequenceNoAsc(100L))
                .willReturn(List.of(stage));
        given(reviewCriterionRepository.findAllForUpdateByContestStageIdInOrderBySortOrderAsc(
                List.of(201L)))
                .willReturn(List.of(criterion));

        StageReq changedCriterion = new StageReq(
                201L,
                "심사",
                StageType.REVIEW,
                1,
                StageStatus.OPEN,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(new CriterionReq(301L, "creativity", "창의성", 40, 1))
        );

        assertThatThrownBy(() -> contestCommandService.updateContest(
                10L,
                "pub-1",
                createReq(List.of(changedCriterion))
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ContestErrorResponseCode.STAGE_CONFIGURATION_LOCKED);
        assertThat(criterion.getMaxScore()).isEqualTo(30);
    }

    @Test
    @DisplayName("열린 단계를 그대로 보내면 대회 기본 정보 수정은 허용한다")
    void updateContest_allowsContestUpdateWhenOpenStageIsUnchanged() {
        Contest contest = contestBuilder(organization);
        ReflectionTestUtils.setField(contest, "id", 100L);
        ContestStage stage =
                stageEntity(contest, 201L, "심사", StageType.REVIEW, 1, StageStatus.OPEN);
        ReviewCriterion criterion =
                criterionEntity(stage, 301L, "creativity", "창의성", 30, 1, true);

        given(userRepository.findById(10L)).willReturn(Optional.of(admin));
        given(contestRepository.findByPublicId("pub-1")).willReturn(Optional.of(contest));
        given(contestStageRepository.findAllForUpdateByContestIdOrderBySequenceNoAsc(100L))
                .willReturn(List.of(stage));
        given(reviewCriterionRepository.findAllForUpdateByContestStageIdInOrderBySortOrderAsc(
                List.of(201L)))
                .willReturn(List.of(criterion));

        StageReq unchangedStage = new StageReq(
                201L,
                "심사",
                StageType.REVIEW,
                1,
                StageStatus.OPEN,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(new CriterionReq(301L, "creativity", "창의성", 30, 1))
        );

        ContestDetailRes res = contestCommandService.updateContest(
                10L,
                "pub-1",
                createReq(List.of(unchangedStage))
        );

        assertThat(res.title()).isEqualTo("2026 AI 공모전");
        assertThat(res.stages().get(0).status()).isEqualTo(StageStatus.OPEN);
        assertThat(res.stages().get(0).criteria().get(0).id()).isEqualTo(301L);
        verify(reviewCriterionRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("준비 중인 단계의 순서를 맞바꿀 때 임시 순서를 거쳐 최종 순서를 적용한다")
    void updateContest_swapsPreparingStageSequencesSafely() {
        Contest contest = contestBuilder(organization);
        ReflectionTestUtils.setField(contest, "id", 100L);
        ContestStage application =
                stageEntity(contest, 201L, "참가 신청", StageType.APPLICATION, 1);
        ContestStage submission =
                stageEntity(contest, 202L, "제출", StageType.SUBMISSION, 2);

        given(userRepository.findById(10L)).willReturn(Optional.of(admin));
        given(contestRepository.findByPublicId("pub-1")).willReturn(Optional.of(contest));
        given(contestStageRepository.findAllForUpdateByContestIdOrderBySequenceNoAsc(100L))
                .willReturn(List.of(application, submission));

        ContestDetailRes res = contestCommandService.updateContest(
                10L,
                "pub-1",
                createReq(List.of(
                        stageReq(201L, "참가 신청", StageType.APPLICATION, 2, null),
                        stageReq(202L, "제출", StageType.SUBMISSION, 1, null)
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
    @DisplayName("뒤 단계의 요청이 잘못되면 앞 단계와 대회 정보도 변경하지 않는다")
    void updateContest_validatesEveryStageBeforeMutation() {
        Contest contest = contestBuilder(organization);
        ReflectionTestUtils.setField(contest, "id", 100L);
        ContestStage application =
                stageEntity(contest, 201L, "참가 신청", StageType.APPLICATION, 1);
        ContestStage review = stageEntity(contest, 202L, "심사", StageType.REVIEW, 2);
        ReviewCriterion criterion =
                criterionEntity(review, 302L, "creativity", "창의성", 30, 1, true);

        given(userRepository.findById(10L)).willReturn(Optional.of(admin));
        given(contestRepository.findByPublicId("pub-1")).willReturn(Optional.of(contest));
        given(contestStageRepository.findAllForUpdateByContestIdOrderBySequenceNoAsc(100L))
                .willReturn(List.of(application, review));
        given(reviewCriterionRepository.findAllForUpdateByContestStageIdInOrderBySortOrderAsc(
                List.of(201L, 202L)))
                .willReturn(List.of(criterion));

        assertThatThrownBy(() -> contestCommandService.updateContest(
                10L,
                "pub-1",
                createReq(List.of(
                        stageReq(
                                201L,
                                "참가 신청(변경)",
                                StageType.APPLICATION,
                                1,
                                null
                        ),
                        stageReq(
                                202L,
                                "심사",
                                StageType.REVIEW,
                                2,
                                List.of(new CriterionReq(
                                        999L,
                                        "creativity",
                                        "창의성",
                                        30,
                                        1
                                ))
                        )
                ))
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ContestErrorResponseCode.REVIEW_CRITERION_NOT_FOUND);

        assertThat(contest.getTitle()).isEqualTo("기존 대회");
        assertThat(application.getName()).isEqualTo("참가 신청");
        assertThat(criterion.getMaxScore()).isEqualTo(30);
        verify(contestStageRepository, never()).flush();
        verify(reviewCriterionRepository, never()).saveAll(anyList());
        verify(reviewCriterionRepository, never()).deleteByContestStageIdIn(anyList());
    }

    @Test
    @DisplayName("단계 상태를 변경하면 변경된 상태가 반환된다")
    void updateStageStatus_changesStatus() {
        Contest contest = contestBuilder(organization);
        ReflectionTestUtils.setField(contest, "id", 100L);
        ReflectionTestUtils.setField(
                contest,
                "status",
                ContestStatus.REVIEWING
        );
        ContestStage stage = stageEntity(contest, 201L, "심사", StageType.REVIEW, 1);
        configureReviewStageForOpening(stage, StagePassRule.FINAL, null, null);
        ReviewCriterion criterion =
                criterionEntity(stage, 301L, "creativity", "창의성", 30, 1, true);
        ReviewRoundEntry entry = ReviewRoundEntry.builder()
                .reviewStage(stage)
                .status(ReviewRoundEntryStatus.ELIGIBLE)
                .build();
        ReflectionTestUtils.setField(entry, "id", 401L);

        given(userRepository.findById(10L)).willReturn(Optional.of(admin));
        given(contestStageRepository.findByIdForUpdate(201L)).willReturn(Optional.of(stage));
        given(reviewCriterionRepository.findAllForUpdateByContestStageIdInOrderBySortOrderAsc(
                List.of(201L)))
                .willReturn(List.of(criterion));
        given(reviewRoundEntryRepository
                .findAllForUpdateByReviewStageIdOrderByIdAsc(201L))
                .willReturn(List.of(entry));

        StageRes res = contestCommandService.updateStageStatus(10L, 201L,
                new StageStatusUpdateReq(StageStatus.OPEN));

        assertThat(res.status()).isEqualTo(StageStatus.OPEN);
        assertThat(entry.getStatus())
                .isEqualTo(ReviewRoundEntryStatus.IN_REVIEW);
        verify(entityManager).refresh(
                contest,
                LockModeType.PESSIMISTIC_READ
        );
    }

    @Test
    @DisplayName("활성 평가 기준이 없는 심사 단계는 시작할 수 없다")
    void updateStageStatus_rejectsOpeningReviewStageWithoutCriterion() {
        Contest contest = contestBuilder(organization);
        ReflectionTestUtils.setField(contest, "id", 100L);
        ContestStage stage = stageEntity(contest, 201L, "심사", StageType.REVIEW, 1);
        configureReviewStageForOpening(stage, StagePassRule.FINAL, null, null);

        given(userRepository.findById(10L)).willReturn(Optional.of(admin));
        given(contestStageRepository.findByIdForUpdate(201L)).willReturn(Optional.of(stage));
        given(reviewCriterionRepository.findAllForUpdateByContestStageIdInOrderBySortOrderAsc(
                List.of(201L)))
                .willReturn(List.of());

        assertThatThrownBy(() -> contestCommandService.updateStageStatus(
                10L,
                201L,
                new StageStatusUpdateReq(StageStatus.OPEN)
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ContestErrorResponseCode.REVIEW_CRITERION_REQUIRED);
        assertThat(stage.getStatus()).isEqualTo(StageStatus.PREPARING);
    }

    @Test
    @DisplayName("심사 대상이 준비되지 않은 심사 단계는 시작할 수 없다")
    void updateStageStatus_rejectsOpeningReviewStageWithoutEntry() {
        Contest contest = contestBuilder(organization);
        ReflectionTestUtils.setField(contest, "id", 100L);
        ContestStage stage =
                stageEntity(contest, 201L, "심사", StageType.REVIEW, 1);
        configureReviewStageForOpening(
                stage,
                StagePassRule.FINAL,
                null,
                null
        );
        ReviewCriterion criterion =
                criterionEntity(
                        stage,
                        301L,
                        "creativity",
                        "창의성",
                        30,
                        1,
                        true
                );

        given(userRepository.findById(10L)).willReturn(Optional.of(admin));
        given(contestStageRepository.findByIdForUpdate(201L))
                .willReturn(Optional.of(stage));
        given(reviewCriterionRepository
                .findAllForUpdateByContestStageIdInOrderBySortOrderAsc(
                        List.of(201L)))
                .willReturn(List.of(criterion));
        given(reviewRoundEntryRepository
                .findAllForUpdateByReviewStageIdOrderByIdAsc(201L))
                .willReturn(List.of());

        assertThatThrownBy(() -> contestCommandService.updateStageStatus(
                10L,
                201L,
                new StageStatusUpdateReq(StageStatus.OPEN)
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode.REVIEW_ENTRY_REQUIRED);
        assertThat(stage.getStatus()).isEqualTo(StageStatus.PREPARING);
        verify(adminAuditLogger, never()).log(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("대회가 REVIEWING 상태가 아니면 준비된 심사 단계도 시작할 수 없다")
    void updateStageStatus_rejectsOpeningWhenContestIsNotReviewing() {
        Contest contest = contestBuilder(organization);
        ReflectionTestUtils.setField(contest, "id", 100L);
        ContestStage stage =
                stageEntity(contest, 201L, "심사", StageType.REVIEW, 1);
        configureReviewStageForOpening(
                stage,
                StagePassRule.FINAL,
                null,
                null
        );
        ReviewCriterion criterion =
                criterionEntity(
                        stage,
                        301L,
                        "creativity",
                        "창의성",
                        30,
                        1,
                        true
                );

        given(userRepository.findById(10L)).willReturn(Optional.of(admin));
        given(contestStageRepository.findByIdForUpdate(201L))
                .willReturn(Optional.of(stage));
        given(reviewCriterionRepository
                .findAllForUpdateByContestStageIdInOrderBySortOrderAsc(
                        List.of(201L)))
                .willReturn(List.of(criterion));

        assertThatThrownBy(() -> contestCommandService.updateStageStatus(
                10L,
                201L,
                new StageStatusUpdateReq(StageStatus.OPEN)
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode
                        .REVIEW_ENTRY_CONTEST_NOT_REVIEWING);

        assertThat(stage.getStatus()).isEqualTo(StageStatus.PREPARING);
        verify(entityManager).refresh(
                contest,
                LockModeType.PESSIMISTIC_READ
        );
    }

    @Test
    @DisplayName("마감 시각이 없는 제출 단계는 시작할 수 없다")
    void updateStageStatus_rejectsSubmissionStageWithoutDeadline() {
        Contest contest = contestBuilder(organization);
        ReflectionTestUtils.setField(contest, "id", 100L);
        ContestStage stage =
                stageEntity(contest, 201L, "제출", StageType.SUBMISSION, 1);

        given(userRepository.findById(10L)).willReturn(Optional.of(admin));
        given(contestStageRepository.findByIdForUpdate(201L))
                .willReturn(Optional.of(stage));
        given(reviewCriterionRepository
                .findAllForUpdateByContestStageIdInOrderBySortOrderAsc(
                        List.of(201L)))
                .willReturn(List.of());

        assertThatThrownBy(() -> contestCommandService.updateStageStatus(
                10L,
                201L,
                new StageStatusUpdateReq(StageStatus.OPEN)
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(
                        ContestErrorResponseCode.STAGE_CONFIGURATION_INVALID);

        assertThat(stage.getStatus()).isEqualTo(StageStatus.PREPARING);
    }

    @Test
    @DisplayName("TOP_N 심사 단계는 통과 팀 수가 없으면 시작할 수 없다")
    void updateStageStatus_rejectsOpeningStageWithInvalidRuleConfiguration() {
        Contest contest = contestBuilder(organization);
        ReflectionTestUtils.setField(contest, "id", 100L);
        ContestStage stage = stageEntity(contest, 201L, "심사", StageType.REVIEW, 1);
        configureReviewStageForOpening(stage, StagePassRule.TOP_N, null, null);
        ReviewCriterion criterion =
                criterionEntity(stage, 301L, "creativity", "창의성", 30, 1, true);

        given(userRepository.findById(10L)).willReturn(Optional.of(admin));
        given(contestStageRepository.findByIdForUpdate(201L)).willReturn(Optional.of(stage));
        given(reviewCriterionRepository.findAllForUpdateByContestStageIdInOrderBySortOrderAsc(
                List.of(201L)))
                .willReturn(List.of(criterion));

        assertThatThrownBy(() -> contestCommandService.updateStageStatus(
                10L,
                201L,
                new StageStatusUpdateReq(StageStatus.OPEN)
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ContestErrorResponseCode.STAGE_CONFIGURATION_INVALID);
        assertThat(stage.getStatus()).isEqualTo(StageStatus.PREPARING);
    }

    @Test
    @DisplayName("단계 상태는 중간 상태를 건너뛰어 변경할 수 없다")
    void updateStageStatus_rejectsSkippedTransition() {
        Contest contest = contestBuilder(organization);
        ReflectionTestUtils.setField(contest, "id", 100L);
        ContestStage stage = stageEntity(contest, 201L, "심사", StageType.REVIEW, 1);

        given(userRepository.findById(10L)).willReturn(Optional.of(admin));
        given(contestStageRepository.findByIdForUpdate(201L)).willReturn(Optional.of(stage));

        assertThatThrownBy(() -> contestCommandService.updateStageStatus(
                10L,
                201L,
                new StageStatusUpdateReq(StageStatus.COMPLETED)
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ContestErrorResponseCode.INVALID_STAGE_STATUS_TRANSITION);
        assertThat(stage.getStatus()).isEqualTo(StageStatus.PREPARING);
    }

    @Test
    @DisplayName("완료된 상태에서 이전 상태로 되돌릴 수 없다")
    void updateStageStatus_rejectsBackwardTransition() {
        Contest contest = contestBuilder(organization);
        ReflectionTestUtils.setField(contest, "id", 100L);
        ContestStage stage =
                stageEntity(contest, 201L, "심사", StageType.REVIEW, 1, StageStatus.COMPLETED);

        given(userRepository.findById(10L)).willReturn(Optional.of(admin));
        given(contestStageRepository.findByIdForUpdate(201L)).willReturn(Optional.of(stage));

        assertThatThrownBy(() -> contestCommandService.updateStageStatus(
                10L,
                201L,
                new StageStatusUpdateReq(StageStatus.OPEN)
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ContestErrorResponseCode.INVALID_STAGE_STATUS_TRANSITION);
        assertThat(stage.getStatus()).isEqualTo(StageStatus.COMPLETED);
    }

    @Test
    @DisplayName("타 조직 단계는 잠그지 않고 상태 변경을 거부한다")
    void updateStageStatus_rejectsOtherOrganizationBeforeLock() {
        given(userRepository.findById(10L)).willReturn(Optional.of(admin));
        given(contestStageRepository.findOrganizationIdById(201L)).willReturn(Optional.of(2L));

        assertThatThrownBy(() -> contestCommandService.updateStageStatus(
                10L,
                201L,
                new StageStatusUpdateReq(StageStatus.OPEN)
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ContestErrorResponseCode.CONTEST_FORBIDDEN);
        verify(contestStageRepository, never()).findByIdForUpdate(201L);
    }

    @Test
    @DisplayName("존재하지 않는 단계 상태 변경 시 STAGE_NOT_FOUND 예외가 발생한다")
    void updateStageStatus_throwsWhenStageNotFound() {
        given(userRepository.findById(10L)).willReturn(Optional.of(admin));
        given(contestStageRepository.findByIdForUpdate(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> contestCommandService.updateStageStatus(10L, 999L,
                new StageStatusUpdateReq(StageStatus.OPEN)))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ContestErrorResponseCode.STAGE_NOT_FOUND);
    }

    //======= 헬퍼 메서드 ==========

    private void stubSaveWithIds() {
        AtomicLong contestIdSequence = new AtomicLong(100L);
        AtomicLong stageIdSequence = new AtomicLong(200L);
        AtomicLong criterionIdSequence = new AtomicLong(300L);
        given(contestRepository.save(any(Contest.class))).willAnswer(invocation -> {
            Contest contest = invocation.getArgument(0);
            ReflectionTestUtils.setField(contest, "id", contestIdSequence.getAndIncrement());
            ReflectionTestUtils.setField(contest, "publicId", "pub-" + contest.getId());
            return contest;
        });
        given(contestStageRepository.save(any(ContestStage.class))).willAnswer(invocation -> {
            ContestStage stage = invocation.getArgument(0);
            ReflectionTestUtils.setField(stage, "id", stageIdSequence.getAndIncrement());
            return stage;
        });
        // 기준 없는 단계만 저장하는 테스트도 있으므로 strict stubbing에서 제외한다
        org.mockito.Mockito.lenient().when(reviewCriterionRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<ReviewCriterion> criteria = invocation.getArgument(0);
            for (ReviewCriterion criterion : criteria) {
                if (criterion.getId() == null) {
                    ReflectionTestUtils.setField(
                            criterion,
                            "id",
                            criterionIdSequence.getAndIncrement()
                    );
                }
            }
            return criteria;
        });
    }

    private void stubCriterionSaveWithIds() {
        AtomicLong criterionIdSequence = new AtomicLong(400L);
        given(reviewCriterionRepository.saveAll(anyList())).willAnswer(invocation -> {
            List<ReviewCriterion> criteria = invocation.getArgument(0);
            for (ReviewCriterion criterion : criteria) {
                if (criterion.getId() == null) {
                    ReflectionTestUtils.setField(criterion, "id", criterionIdSequence.getAndIncrement());
                }
            }
            return criteria;
        });
    }

    private ContestCreateReq createReq(List<StageReq> stages) {
        return createReqWithDetailHtml("<p>본문</p>", stages);
    }

    private ContestCreateReq createReqWithDetailHtml(String detailHtml, List<StageReq> stages) {
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
                stages);
    }

    private StageReq stageReq(Long id, String name, StageType stageType, int sequenceNo,
                              List<CriterionReq> criteria) {
        return new StageReq(id, name, stageType, sequenceNo, StageStatus.PREPARING,
                null, null, StageTargetType.ALL_SUBMISSIONS, StagePassRule.FINAL, null, null, criteria);
    }

    private Contest contestBuilder(Organization contestOrganization) {
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

    private ContestStage stageEntity(Contest contest, Long id, String name, StageType stageType, int sequenceNo) {
        return stageEntity(contest, id, name, stageType, sequenceNo, StageStatus.PREPARING);
    }

    private ContestStage stageEntity(
            Contest contest,
            Long id,
            String name,
            StageType stageType,
            int sequenceNo,
            StageStatus status
    ) {
        ContestStage stage = ContestStage.builder()
                .contest(contest)
                .name(name)
                .stageType(stageType)
                .sequenceNo(sequenceNo)
                .status(status)
                .build();
        ReflectionTestUtils.setField(stage, "id", id);
        return stage;
    }

    private ReviewCriterion criterionEntity(
            ContestStage stage,
            Long id,
            String code,
            String label,
            int maxScore,
            int sortOrder,
            boolean active
    ) {
        ReviewCriterion criterion = ReviewCriterion.builder()
                .contestStage(stage)
                .code(code)
                .label(label)
                .maxScore(maxScore)
                .sortOrder(sortOrder)
                .active(active)
                .build();
        ReflectionTestUtils.setField(criterion, "id", id);
        return criterion;
    }

    private void configureReviewStageForOpening(
            ContestStage stage,
            StagePassRule passRule,
            Integer passCount,
            java.math.BigDecimal minScore
    ) {
        stage.updateConfiguration(
                stage.getName(),
                stage.getStageType(),
                stage.getSequenceNo(),
                stage.getStartsAt(),
                stage.getEndsAt(),
                StageTargetType.ALL_SUBMISSIONS,
                passRule,
                passCount,
                minScore
        );
    }
}
