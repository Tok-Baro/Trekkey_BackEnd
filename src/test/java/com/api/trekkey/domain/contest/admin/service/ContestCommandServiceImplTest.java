package com.api.trekkey.domain.contest.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
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
import com.api.trekkey.domain.contest.admin.web.dto.ContestCreateReq;
import com.api.trekkey.domain.contest.web.dto.ContestDetailRes;
import com.api.trekkey.domain.contest.admin.web.dto.CriterionReq;
import com.api.trekkey.domain.contest.admin.web.dto.StageReq;
import com.api.trekkey.domain.contest.web.dto.StageRes;
import com.api.trekkey.domain.contest.admin.web.dto.StageStatusUpdateReq;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.user.entity.MemberType;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
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
                new ContestHtmlSanitizer(),
                adminAuditLogger);

        organization = org.mockito.Mockito.mock(Organization.class);
        // 일부 테스트는 조직 검증 전에 예외로 종료되므로 strict stubbing에서 제외한다
        org.mockito.Mockito.lenient().when(organization.getId()).thenReturn(1L);

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
        given(contestStageRepository.findAllByContestIdOrderBySequenceNoAsc(100L))
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
        given(contestStageRepository.findAllByContestIdOrderBySequenceNoAsc(100L)).willReturn(List.of());

        assertThatThrownBy(() -> contestCommandService.updateContest(10L, "pub-1",
                createReq(List.of(stageReq(999L, "참가 신청", StageType.APPLICATION, 1, null)))))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ContestErrorResponseCode.STAGE_NOT_FOUND);
    }

    @Test
    @DisplayName("단계 상태를 변경하면 변경된 상태가 반환된다")
    void updateStageStatus_changesStatus() {
        Contest contest = contestBuilder(organization);
        ReflectionTestUtils.setField(contest, "id", 100L);
        ContestStage stage = stageEntity(contest, 201L, "심사", StageType.REVIEW, 1);

        given(userRepository.findById(10L)).willReturn(Optional.of(admin));
        given(contestStageRepository.findById(201L)).willReturn(Optional.of(stage));
        given(reviewCriterionRepository.findAllByContestStageIdInOrderBySortOrderAsc(List.of(201L)))
                .willReturn(List.of());

        StageRes res = contestCommandService.updateStageStatus(10L, 201L,
                new StageStatusUpdateReq(StageStatus.OPEN));

        assertThat(res.status()).isEqualTo(StageStatus.OPEN);
    }

    @Test
    @DisplayName("존재하지 않는 단계 상태 변경 시 STAGE_NOT_FOUND 예외가 발생한다")
    void updateStageStatus_throwsWhenStageNotFound() {
        given(userRepository.findById(10L)).willReturn(Optional.of(admin));
        given(contestStageRepository.findById(999L)).willReturn(Optional.empty());

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
            long id = 300L;
            for (ReviewCriterion criterion : criteria) {
                ReflectionTestUtils.setField(criterion, "id", id++);
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
        ContestStage stage = ContestStage.builder()
                .contest(contest)
                .name(name)
                .stageType(stageType)
                .sequenceNo(sequenceNo)
                .status(StageStatus.PREPARING)
                .build();
        ReflectionTestUtils.setField(stage, "id", id);
        return stage;
    }
}
