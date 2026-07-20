package com.api.trekkey.domain.contest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestLike;
import com.api.trekkey.domain.contest.entity.ContestStage;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.StageType;
import com.api.trekkey.domain.contest.repository.ContestLikeRepository;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.contest.repository.ContestStageRepository;
import com.api.trekkey.domain.contest.web.dto.ContestSearchRes;
import com.api.trekkey.domain.contest.web.dto.ContestSearchStatus;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContestServiceImplTest {

    @Mock
    private ContestRepository contestRepository;

    @Mock
    private ContestStageRepository contestStageRepository;

    @Mock
    private ContestLikeRepository contestLikeRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ContestServiceImpl contestService;

    @Test
    @DisplayName("검색어와 접수중 필터를 적용하고 카드 응답을 조립한다")
    void searchContests_returnsContestCards() {
        givenParticipant(10L, 2L);
        Contest contest = contest();
        LocalDateTime submissionDueAt = LocalDateTime.of(2026, 8, 10, 23, 59);
        ContestStage submissionStage = ContestStage.builder()
                .contest(contest)
                .stageType(StageType.SUBMISSION)
                .endsAt(submissionDueAt)
                .build();
        ContestLike contestLike = ContestLike.builder()
                .contest(contest)
                .build();

        given(contestRepository.searchContests(
                2L,
                "AI",
                Set.of(ContestStatus.APPLICATION_OPEN)))
                .willReturn(List.of(contest));
        given(contestStageRepository.findAllByContestIdInAndStageTypeOrderBySequenceNoAsc(
                List.of(1L),
                StageType.SUBMISSION))
                .willReturn(List.of(submissionStage));
        given(contestLikeRepository.findAllByContestIdIn(List.of(1L))).willReturn(List.of(contestLike));

        List<ContestSearchRes> result = contestService.searchContests(10L, "  AI  ", ContestSearchStatus.OPEN);

        assertThat(result).containsExactly(new ContestSearchRes(
                "f04739b5-bb66-4c3f-bf91-31b8712011be",
                "AI 창의 경진대회",
                ContestStatus.APPLICATION_OPEN,
                "https://example.com/poster.png",
                "AI로 해결하는 캠퍼스 문제",
                List.of("AI", "캠퍼스"),
                submissionDueAt,
                31L,
                1L));
    }

    @Test
    @DisplayName("전체 필터는 공개 상태만 조회하고 공백 검색어를 제거한다")
    void searchContests_allExcludesPreparingAndNormalizesBlankKeyword() {
        givenParticipant(10L, 2L);
        Set<ContestStatus> publicStatuses = Set.of(
                ContestStatus.APPLICATION_OPEN,
                ContestStatus.REVIEWING,
                ContestStatus.AWARDED);
        given(contestRepository.searchContests(2L, "", publicStatuses)).willReturn(List.of());

        List<ContestSearchRes> result = contestService.searchContests(10L, "   ", ContestSearchStatus.ALL);

        assertThat(result).isEmpty();
        verify(contestRepository).searchContests(2L, "", publicStatuses);
        verifyNoInteractions(contestStageRepository, contestLikeRepository);
    }

    @Test
    @DisplayName("종료 필터는 심사중과 수상확정 대회를 조회한다")
    void searchContests_closedMapsToReviewingAndAwarded() {
        givenParticipant(10L, 2L);
        Set<ContestStatus> closedStatuses = Set.of(
                ContestStatus.REVIEWING,
                ContestStatus.AWARDED);
        given(contestRepository.searchContests(2L, "", closedStatuses)).willReturn(List.of());

        assertThat(contestService.searchContests(10L, null, ContestSearchStatus.CLOSED)).isEmpty();

        verify(contestRepository).searchContests(2L, "", closedStatuses);
    }

    @Test
    @DisplayName("로그인 사용자를 찾을 수 없으면 사용자 없음으로 처리한다")
    void searchContests_throwsWhenUserDoesNotExist() {
        given(userRepository.findById(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> contestService.searchContests(10L, null, ContestSearchStatus.OPEN))
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(UserErrorResponseCode.USER_NOT_FOUND);

        verifyNoInteractions(contestRepository, contestStageRepository, contestLikeRepository);
    }

    private void givenParticipant(Long userId, Long organizationId) {
        User user = mock(User.class);
        Organization organization = mock(Organization.class);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(user.getOrganization()).willReturn(organization);
        given(organization.getId()).willReturn(organizationId);
    }

    private Contest contest() {
        return Contest.builder()
                .id(1L)
                .publicId("f04739b5-bb66-4c3f-bf91-31b8712011be")
                .title("AI 창의 경진대회")
                .department("SW중심대학사업단")
                .status(ContestStatus.APPLICATION_OPEN)
                .posterUrl("https://example.com/poster.png")
                .summary("AI로 해결하는 캠퍼스 문제")
                .tags(" AI, 캠퍼스 ")
                .viewCount(31L)
                .build();
    }
}
