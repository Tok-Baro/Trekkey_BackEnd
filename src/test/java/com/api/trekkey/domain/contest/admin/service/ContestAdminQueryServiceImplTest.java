package com.api.trekkey.domain.contest.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.api.trekkey.domain.contest.admin.web.dto.ContestAdminSearchCond;
import com.api.trekkey.domain.contest.admin.web.dto.ContestAdminSummaryRes;
import com.api.trekkey.domain.contest.admin.web.dto.ContestSortKey;
import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStage;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.ParticipationType;
import com.api.trekkey.domain.contest.entity.StageStatus;
import com.api.trekkey.domain.contest.entity.StageType;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.repository.ContestQueryRepository;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.contest.repository.ContestStageRepository;
import com.api.trekkey.domain.contest.web.dto.ContestDetailRes;
import com.api.trekkey.domain.contest.web.dto.StageRes;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.user.entity.MemberType;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import com.api.trekkey.global.response.PageRes;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContestAdminQueryServiceImplTest {

    @Mock
    private ContestQueryRepository contestQueryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ContestRepository contestRepository;

    @Mock
    private ContestStageRepository contestStageRepository;

    private ContestAdminQueryServiceImpl contestAdminQueryService;

    private Organization organization;
    private User admin;

    @BeforeEach
    void setUp() {
        contestAdminQueryService = new ContestAdminQueryServiceImpl(
                contestQueryRepository,
                userRepository,
                contestRepository,
                contestStageRepository
        );
        organization = organization(1L);
        admin = user(10L, organization, UserRole.ADMIN, UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("관리자 대회 목록은 담당자, 일정, 운영 건수를 한 번에 반환한다")
    void getContests_returnsEnrichedSummaries() {
        User secondOwner = user(
                11L,
                organization,
                UserRole.ADMIN,
                UserStatus.ACTIVE
        );
        Contest firstContest = contest(
                100L,
                "contest-one",
                organization,
                admin
        );
        Contest secondContest = contest(
                101L,
                "contest-two",
                organization,
                secondOwner
        );
        ContestStage application = stage(
                201L,
                firstContest,
                "참가 신청",
                StageType.APPLICATION,
                1,
                LocalDateTime.of(2026, 8, 1, 9, 0),
                LocalDateTime.of(2026, 8, 10, 18, 0)
        );
        ContestStage submission = stage(
                202L,
                firstContest,
                "작품 제출",
                StageType.SUBMISSION,
                2,
                LocalDateTime.of(2026, 8, 11, 9, 0),
                LocalDateTime.of(2026, 8, 20, 23, 59)
        );
        ContestAdminSearchCond cond = new ContestAdminSearchCond(
                null,
                null,
                ContestSortKey.TITLE,
                "ASC",
                0,
                20
        );
        List<Long> contestIds = List.of(100L, 101L);
        Set<StageType> summaryStageTypes = Set.of(
                StageType.APPLICATION,
                StageType.SUBMISSION
        );
        given(userRepository.findById(10L)).willReturn(Optional.of(admin));
        given(contestQueryRepository.findAdminContests(1L, cond))
                .willReturn(List.of(firstContest, secondContest));
        given(contestQueryRepository.countAdminContests(1L, cond))
                .willReturn(2L);
        given(contestStageRepository
                .findAllByContestIdInAndStageTypeInOrderByContestIdAscSequenceNoAsc(
                        contestIds,
                        summaryStageTypes))
                .willReturn(List.of(application, submission));
        given(contestQueryRepository.countTeamsByContestIds(contestIds))
                .willReturn(Map.of(100L, 2L, 101L, 1L));
        given(contestQueryRepository.countSubmissionsByContestIds(contestIds))
                .willReturn(Map.of(100L, 1L));
        given(contestQueryRepository.countJudgesByContestIds(contestIds))
                .willReturn(Map.of(100L, 3L, 101L, 1L));

        PageRes<ContestAdminSummaryRes> response =
                contestAdminQueryService.getContests(10L, cond);

        assertThat(response.content())
                .extracting(ContestAdminSummaryRes::id)
                .containsExactly("contest-one", "contest-two");
        ContestAdminSummaryRes first = response.content().getFirst();
        assertThat(first.ownerName()).isEqualTo("관리자");
        assertThat(first.applicationStartsAt()).isEqualTo(application.getStartsAt());
        assertThat(first.applicationEndsAt()).isEqualTo(application.getEndsAt());
        assertThat(first.submissionDueAt()).isEqualTo(submission.getEndsAt());
        assertThat(first.teamCount()).isEqualTo(2L);
        assertThat(first.submissionCount()).isEqualTo(1L);
        assertThat(first.judgeCount()).isEqualTo(3L);
        ContestAdminSummaryRes second = response.content().get(1);
        assertThat(second.applicationStartsAt()).isNull();
        assertThat(second.applicationEndsAt()).isNull();
        assertThat(second.submissionDueAt()).isNull();
        assertThat(second.teamCount()).isEqualTo(1L);
        assertThat(second.submissionCount()).isZero();
        assertThat(second.judgeCount()).isEqualTo(1L);
        assertThat(response.totalElements()).isEqualTo(2L);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.hasNext()).isFalse();
        verify(contestQueryRepository).countTeamsByContestIds(contestIds);
        verify(contestQueryRepository).countSubmissionsByContestIds(contestIds);
        verify(contestQueryRepository).countJudgesByContestIds(contestIds);
    }

    @Test
    @DisplayName("관리자 대회 목록이 비어 있으면 요약 배치 조회를 실행하지 않는다")
    void getContests_skipsSummaryQueriesForEmptyPage() {
        ContestAdminSearchCond cond = new ContestAdminSearchCond(
                null,
                null,
                null,
                null,
                0,
                20
        );
        given(userRepository.findById(10L)).willReturn(Optional.of(admin));
        given(contestQueryRepository.findAdminContests(1L, cond))
                .willReturn(List.of());
        given(contestQueryRepository.countAdminContests(1L, cond))
                .willReturn(0L);

        PageRes<ContestAdminSummaryRes> response =
                contestAdminQueryService.getContests(10L, cond);

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
        assertThat(response.hasNext()).isFalse();
        verifyNoInteractions(contestStageRepository);
        verify(contestQueryRepository, never()).countTeamsByContestIds(anyCollection());
        verify(contestQueryRepository, never()).countSubmissionsByContestIds(anyCollection());
        verify(contestQueryRepository, never()).countJudgesByContestIds(anyCollection());
    }

    @Test
    @DisplayName("관리자는 준비 중인 대회의 전체 설정과 단계를 순서대로 조회한다")
    void getContest_returnsFullContestWithStages() {
        Contest contest = contest(100L, organization);
        ContestStage application = stage(
                201L,
                contest,
                "참가 신청",
                StageType.APPLICATION,
                1,
                LocalDateTime.of(2026, 8, 1, 9, 0),
                LocalDateTime.of(2026, 8, 10, 18, 0)
        );
        ContestStage submission = stage(
                202L,
                contest,
                "작품 제출",
                StageType.SUBMISSION,
                2,
                LocalDateTime.of(2026, 8, 11, 9, 0),
                LocalDateTime.of(2026, 8, 20, 23, 59)
        );
        ContestStage migratedReview = stage(
                203L,
                contest,
                "이전 1차 심사",
                StageType.REVIEW,
                3,
                LocalDateTime.of(2026, 8, 21, 9, 0),
                LocalDateTime.of(2026, 8, 22, 18, 0)
        );
        given(userRepository.findById(10L))
                .willReturn(Optional.of(admin));
        given(contestRepository.findByPublicId("contest-public-id"))
                .willReturn(Optional.of(contest));
        given(contestStageRepository
                .findAllByContestIdOrderBySequenceNoAsc(100L))
                .willReturn(List.of(
                        application,
                        submission,
                        migratedReview));

        ContestDetailRes response = contestAdminQueryService.getContest(
                10L,
                "contest-public-id"
        );

        assertThat(response.id()).isEqualTo("contest-public-id");
        assertThat(response.status()).isEqualTo(ContestStatus.PREPARING);
        assertThat(response.detailHtml()).isEqualTo("<p>상세 안내</p>");
        assertThat(response.applicationStartsAt())
                .isEqualTo(application.getStartsAt());
        assertThat(response.applicationEndsAt())
                .isEqualTo(application.getEndsAt());
        assertThat(response.submissionDueAt())
                .isEqualTo(submission.getEndsAt());
        assertThat(response.stages())
                .extracting(StageRes::id)
                .containsExactly(201L, 202L);
        assertThat(response.stages())
                .allSatisfy(stageResponse ->
                        assertThat(stageResponse.criteria()).isEmpty());
    }

    @Test
    @DisplayName("다른 조직의 대회 상세는 조회할 수 없다")
    void getContest_rejectsOtherOrganization() {
        Contest contest = contest(100L, organization(2L));
        given(userRepository.findById(10L))
                .willReturn(Optional.of(admin));
        given(contestRepository.findByPublicId("contest-public-id"))
                .willReturn(Optional.of(contest));

        assertThatThrownBy(() -> contestAdminQueryService.getContest(
                10L,
                "contest-public-id"
        ))
                .isInstanceOf(CustomException.class)
                .extracting(error ->
                        ((CustomException) error).getBaseResponseCode())
                .isEqualTo(ContestErrorResponseCode.CONTEST_FORBIDDEN);
        verify(contestStageRepository, never())
                .findAllByContestIdOrderBySequenceNoAsc(100L);
    }

    @Test
    @DisplayName("존재하지 않는 대회의 상세 조회는 404 오류로 처리한다")
    void getContest_rejectsMissingContest() {
        given(userRepository.findById(10L))
                .willReturn(Optional.of(admin));
        given(contestRepository.findByPublicId("missing-contest"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> contestAdminQueryService.getContest(
                10L,
                "missing-contest"
        ))
                .isInstanceOf(CustomException.class)
                .extracting(error ->
                        ((CustomException) error).getBaseResponseCode())
                .isEqualTo(ContestErrorResponseCode.CONTEST_NOT_FOUND);
    }

    @Test
    @DisplayName("비활성 관리자는 대회 상세를 조회할 수 없다")
    void getContest_rejectsInactiveAdmin() {
        User inactiveAdmin = user(
                10L,
                organization,
                UserRole.ADMIN,
                UserStatus.INACTIVE
        );
        given(userRepository.findById(10L))
                .willReturn(Optional.of(inactiveAdmin));

        assertThatThrownBy(() -> contestAdminQueryService.getContest(
                10L,
                "contest-public-id"
        ))
                .isInstanceOf(CustomException.class)
                .extracting(error ->
                        ((CustomException) error).getBaseResponseCode())
                .isEqualTo(UserErrorResponseCode.USER_INVALID_TOKEN);
        verify(contestRepository, never()).findByPublicId("contest-public-id");
    }

    private Organization organization(Long id) {
        Organization result = org.mockito.Mockito.mock(Organization.class);
        org.mockito.Mockito.lenient()
                .when(result.getId())
                .thenReturn(id);
        return result;
    }

    private User user(
            Long id,
            Organization userOrganization,
            UserRole role,
            UserStatus status
    ) {
        return User.builder()
                .id(id)
                .organization(userOrganization)
                .name("관리자")
                .email("admin-" + id + "@test.com")
                .password("encoded")
                .role(role)
                .memberType(MemberType.STAFF)
                .status(status)
                .build();
    }

    private Contest contest(Long id, Organization contestOrganization) {
        return contest(
                id,
                "contest-public-id",
                contestOrganization,
                admin
        );
    }

    private Contest contest(
            Long id,
            String publicId,
            Organization contestOrganization,
            User owner
    ) {
        return Contest.builder()
                .id(id)
                .publicId(publicId)
                .organization(contestOrganization)
                .ownerUser(owner)
                .title("AI 창의 경진대회")
                .department("SW중심대학사업단")
                .status(ContestStatus.PREPARING)
                .participationType(ParticipationType.BOTH)
                .awardCount(3)
                .posterUrl("https://example.com/poster.png")
                .summary("AI로 해결하는 캠퍼스 문제")
                .target("전체 재학생")
                .applicationMethod("온라인 신청")
                .benefits("우수팀 시상")
                .tags("AI,캠퍼스")
                .detailHtml("<p>상세 안내</p>")
                .viewCount(31L)
                .build();
    }

    private ContestStage stage(
            Long id,
            Contest contest,
            String name,
            StageType stageType,
            int sequenceNo,
            LocalDateTime startsAt,
            LocalDateTime endsAt
    ) {
        return ContestStage.builder()
                .id(id)
                .contest(contest)
                .name(name)
                .stageType(stageType)
                .sequenceNo(sequenceNo)
                .status(StageStatus.PREPARING)
                .startsAt(startsAt)
                .endsAt(endsAt)
                .build();
    }
}
