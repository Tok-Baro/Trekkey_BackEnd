package com.api.trekkey.domain.contest.publicapi.web.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.ParticipationType;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.publicapi.service.ContestService;
import com.api.trekkey.domain.contest.publicapi.web.dto.ContestDetailRes;
import com.api.trekkey.domain.contest.publicapi.web.dto.ContestLikeRes;
import com.api.trekkey.domain.contest.publicapi.web.dto.ContestSearchRes;
import com.api.trekkey.domain.contest.publicapi.web.dto.ContestSearchStatus;
import com.api.trekkey.global.exception.CustomException;
import com.api.trekkey.global.exception.GlobalExceptionHandler;
import com.api.trekkey.global.security.AuthPrincipal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ContestControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ContestService contestService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(participantAuthentication());
        ContestController contestController = new ContestController(contestService);
        mockMvc = MockMvcBuilders.standaloneSetup(contestController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("대회 검색 결과를 SuccessResponse로 반환한다")
    void searchContests_returnsSuccessResponse() throws Exception {
        ContestSearchRes response = response();
        given(contestService.searchContests(10L, "AI", ContestSearchStatus.CLOSED))
                .willReturn(List.of(response));

        mockMvc.perform(get("/api/contests")
                        .param("keyword", "AI")
                        .param("status", "CLOSED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS_200"))
                .andExpect(jsonPath("$.httpStatus").value(200))
                .andExpect(jsonPath("$.data[0].publicId").value("f04739b5-bb66-4c3f-bf91-31b8712011be"))
                .andExpect(jsonPath("$.data[0].status").value("REVIEWING"))
                .andExpect(jsonPath("$.data[0].tags[0]").value("AI"))
                .andExpect(jsonPath("$.data[0].submissionDueAt").exists())
                .andExpect(jsonPath("$.data[0].department").doesNotExist())
                .andExpect(jsonPath("$.data[0].applicationStartsAt").doesNotExist())
                .andExpect(jsonPath("$.data[0].applicationEndsAt").doesNotExist())
                .andExpect(jsonPath("$.data[0].viewCount").value(31))
                .andExpect(jsonPath("$.data[0].likeCount").value(7))
                .andExpect(jsonPath("$.data[0].likedByMe").value(true));
    }

    @Test
    @DisplayName("상태 파라미터가 없으면 접수중을 기본으로 조회한다")
    void searchContests_usesOpenAsDefaultStatus() throws Exception {
        given(contestService.searchContests(10L, null, ContestSearchStatus.OPEN)).willReturn(List.of());

        mockMvc.perform(get("/api/contests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());

        verify(contestService).searchContests(10L, null, ContestSearchStatus.OPEN);
    }

    @Test
    @DisplayName("지원하지 않는 상태 필터는 400을 반환한다")
    void searchContests_returnsBadRequestForUnknownStatus() throws Exception {
        mockMvc.perform(get("/api/contests")
                        .param("status", "ENDED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("GLOBAL_400_PARAMETER"));

        verifyNoInteractions(contestService);
    }

    @Test
    @DisplayName("대회 단건 상세를 SuccessResponse로 반환한다")
    void getContestDetail_returnsSuccessResponse() throws Exception {
        String publicId = "f04739b5-bb66-4c3f-bf91-31b8712011be";
        given(contestService.getContestDetail(10L, publicId)).willReturn(detailResponse());

        mockMvc.perform(get("/api/contests/{publicId}", publicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS_200"))
                .andExpect(jsonPath("$.data.publicId").value(publicId))
                .andExpect(jsonPath("$.data.status").value("APPLICATION_OPEN"))
                .andExpect(jsonPath("$.data.participationType").value("BOTH"))
                .andExpect(jsonPath("$.data.department").value("SW중심대학사업단"))
                .andExpect(jsonPath("$.data.applicationStartsAt").exists())
                .andExpect(jsonPath("$.data.applicationEndsAt").exists())
                .andExpect(jsonPath("$.data.submissionDueAt").exists())
                .andExpect(jsonPath("$.data.awardCount").value(3))
                .andExpect(jsonPath("$.data.detailHtml").value("<p>대회 상세</p>"))
                .andExpect(jsonPath("$.data.viewCount").value(31))
                .andExpect(jsonPath("$.data.likeCount").value(7))
                .andExpect(jsonPath("$.data.likedByMe").value(true));

        verify(contestService).getContestDetail(10L, publicId);
    }

    @Test
    @DisplayName("좋아요를 토글하고 변경된 상태와 개수를 반환한다")
    void toggleLike_returnsChangedLikeState() throws Exception {
        String publicId = "f04739b5-bb66-4c3f-bf91-31b8712011be";
        given(contestService.toggleLike(10L, publicId))
                .willReturn(new ContestLikeRes(8L, true));

        mockMvc.perform(post("/api/contests/{publicId}/like", publicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.likeCount").value(8))
                .andExpect(jsonPath("$.data.likedByMe").value(true));

        verify(contestService).toggleLike(10L, publicId);
    }

    @Test
    @DisplayName("대회 상세를 찾을 수 없으면 404를 반환한다")
    void getContestDetail_returnsNotFound() throws Exception {
        String publicId = "missing-contest";
        given(contestService.getContestDetail(10L, publicId))
                .willThrow(new CustomException(ContestErrorResponseCode.CONTEST_NOT_FOUND));

        mockMvc.perform(get("/api/contests/{publicId}", publicId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("CONTEST_NOT_FOUND"));
    }

    private ContestSearchRes response() {
        return new ContestSearchRes(
                "f04739b5-bb66-4c3f-bf91-31b8712011be",
                "AI 창의 경진대회",
                ContestStatus.REVIEWING,
                "https://example.com/poster.png",
                "AI로 해결하는 캠퍼스 문제",
                List.of("AI", "캠퍼스"),
                LocalDateTime.of(2026, 8, 10, 23, 59),
                31L,
                7L,
                true);
    }

    private ContestDetailRes detailResponse() {
        return new ContestDetailRes(
                "f04739b5-bb66-4c3f-bf91-31b8712011be",
                "AI 창의 경진대회",
                ContestStatus.APPLICATION_OPEN,
                ParticipationType.BOTH,
                "https://example.com/poster.png",
                "AI로 해결하는 캠퍼스 문제",
                List.of("AI", "캠퍼스"),
                "SW중심대학사업단",
                LocalDateTime.of(2026, 7, 1, 9, 0),
                LocalDateTime.of(2026, 7, 20, 18, 0),
                LocalDateTime.of(2026, 8, 10, 23, 59),
                "전체 재학생",
                3,
                "온라인 신청서 제출",
                "우수팀 시상",
                "<p>대회 상세</p>",
                31L,
                7L,
                true);
    }

    private Authentication participantAuthentication() {
        AuthPrincipal principal = AuthPrincipal.of(10L, "participant@example.com", List.of("PARTICIPANT"));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
