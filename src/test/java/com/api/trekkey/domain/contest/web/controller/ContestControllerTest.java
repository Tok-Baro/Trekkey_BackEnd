package com.api.trekkey.domain.contest.web.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.service.ContestService;
import com.api.trekkey.domain.contest.web.dto.ContestSearchRes;
import com.api.trekkey.domain.contest.web.dto.ContestSearchStatus;
import com.api.trekkey.global.exception.GlobalExceptionHandler;
import com.api.trekkey.global.security.AuthPrincipal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ContestControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ContestService contestService;

    @BeforeEach
    void setUp() {
        ContestController contestController = new ContestController(contestService);
        mockMvc = MockMvcBuilders.standaloneSetup(contestController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("대회 검색 결과를 SuccessResponse로 반환한다")
    void searchContests_returnsSuccessResponse() throws Exception {
        ContestSearchRes response = response();
        given(contestService.searchContests(10L, "AI", ContestSearchStatus.CLOSED))
                .willReturn(List.of(response));

        mockMvc.perform(get("/api/contests")
                        .param("keyword", "AI")
                        .param("status", "CLOSED")
                        .principal(participantAuthentication()))
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
                .andExpect(jsonPath("$.data[0].likeCount").value(7));
    }

    @Test
    @DisplayName("상태 파라미터가 없으면 접수중을 기본으로 조회한다")
    void searchContests_usesOpenAsDefaultStatus() throws Exception {
        given(contestService.searchContests(10L, null, ContestSearchStatus.OPEN)).willReturn(List.of());

        mockMvc.perform(get("/api/contests").principal(participantAuthentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());

        verify(contestService).searchContests(10L, null, ContestSearchStatus.OPEN);
    }

    @Test
    @DisplayName("지원하지 않는 상태 필터는 400을 반환한다")
    void searchContests_returnsBadRequestForUnknownStatus() throws Exception {
        mockMvc.perform(get("/api/contests")
                        .param("status", "ENDED")
                        .principal(participantAuthentication()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("GLOBAL_400_PARAMETER"));

        verifyNoInteractions(contestService);
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
                7L);
    }

    private Authentication participantAuthentication() {
        AuthPrincipal principal = AuthPrincipal.of(10L, "participant@example.com", List.of("PARTICIPANT"));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
