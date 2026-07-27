package com.api.trekkey.domain.submission.publicapi.web.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.trekkey.domain.submission.entity.SubmissionStatus;
import com.api.trekkey.domain.submission.exception.SubmissionErrorResponseCode;
import com.api.trekkey.domain.submission.publicapi.service.SubmissionService;
import com.api.trekkey.domain.submission.publicapi.web.dto.SubmissionRes;
import com.api.trekkey.domain.submission.publicapi.web.dto.SubmissionSaveReq;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class SubmissionControllerTest {

    private static final String CONTEST_PUBLIC_ID = "contest-public-id";

    private MockMvc mockMvc;

    @Mock
    private SubmissionService submissionService;

    @BeforeEach
    void setUp() {
        AuthPrincipal principal =
                AuthPrincipal.of(10L, "participant@example.com", List.of("PARTICIPANT"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()
                )
        );
        SubmissionController controller =
                new SubmissionController(submissionService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("현재 참가자의 제출물을 조회한다")
    void getSubmission_returnsOwnedSubmission() throws Exception {
        given(submissionService.getSubmission(10L, CONTEST_PUBLIC_ID))
                .willReturn(response(SubmissionStatus.SUBMITTED));

        mockMvc.perform(get(
                        "/api/contests/{publicId}/submission",
                        CONTEST_PUBLIC_ID
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.message").value("제출물을 조회했습니다."))
                .andExpect(jsonPath("$.data.publicId")
                        .value("submission-public-id"))
                .andExpect(jsonPath("$.data.title").value("AI 캠퍼스"))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.teamId").doesNotExist())
                .andExpect(jsonPath("$.data.userId").doesNotExist());

        verify(submissionService)
                .getSubmission(10L, CONTEST_PUBLIC_ID);
    }

    @Test
    @DisplayName("작품명을 받아 제출물 초안을 저장한다")
    void saveDraft_returnsSavedDraft() throws Exception {
        SubmissionSaveReq request = new SubmissionSaveReq("AI 캠퍼스");
        given(submissionService.saveDraft(
                10L,
                CONTEST_PUBLIC_ID,
                request
        )).willReturn(response(SubmissionStatus.DRAFT));

        mockMvc.perform(put(
                        "/api/contests/{publicId}/submission",
                        CONTEST_PUBLIC_ID
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"AI 캠퍼스\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS_200"))
                .andExpect(jsonPath("$.message")
                        .value("제출물 초안을 저장했습니다."))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        verify(submissionService)
                .saveDraft(10L, CONTEST_PUBLIC_ID, request);
    }

    @Test
    @DisplayName("작품명이 비어 있으면 서비스 호출 없이 400을 반환한다")
    void saveDraft_rejectsBlankTitle() throws Exception {
        mockMvc.perform(put(
                        "/api/contests/{publicId}/submission",
                        CONTEST_PUBLIC_ID
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL_400_BODY"))
                .andExpect(jsonPath("$.data[0].field").value("title"));

        verifyNoInteractions(submissionService);
    }

    @Test
    @DisplayName("초안을 최종 제출한다")
    void submit_returnsSubmittedResult() throws Exception {
        given(submissionService.submit(10L, CONTEST_PUBLIC_ID))
                .willReturn(response(SubmissionStatus.SUBMITTED));

        mockMvc.perform(post(
                        "/api/contests/{publicId}/submission/submit",
                        CONTEST_PUBLIC_ID
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("제출물을 제출했습니다."))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));

        verify(submissionService).submit(10L, CONTEST_PUBLIC_ID);
    }

    @Test
    @DisplayName("제출된 작품을 초안으로 다시 연다")
    void reopen_returnsDraftResult() throws Exception {
        given(submissionService.reopen(10L, CONTEST_PUBLIC_ID))
                .willReturn(response(SubmissionStatus.DRAFT));

        mockMvc.perform(post(
                        "/api/contests/{publicId}/submission/reopen",
                        CONTEST_PUBLIC_ID
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("제출물 수정을 다시 시작했습니다."))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        verify(submissionService).reopen(10L, CONTEST_PUBLIC_ID);
    }

    @Test
    @DisplayName("제출된 작품을 철회한다")
    void withdraw_returnsWithdrawnResult() throws Exception {
        given(submissionService.withdraw(10L, CONTEST_PUBLIC_ID))
                .willReturn(response(SubmissionStatus.WITHDRAWN));

        mockMvc.perform(post(
                        "/api/contests/{publicId}/submission/withdraw",
                        CONTEST_PUBLIC_ID
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("제출물을 철회했습니다."))
                .andExpect(jsonPath("$.data.status").value("WITHDRAWN"));

        verify(submissionService).withdraw(10L, CONTEST_PUBLIC_ID);
    }

    @Test
    @DisplayName("허용되지 않은 제출물 상태 전이는 409를 반환한다")
    void submit_returnsConflictForInvalidTransition() throws Exception {
        willThrow(new CustomException(
                SubmissionErrorResponseCode.INVALID_SUBMISSION_STATUS_TRANSITION))
                .given(submissionService)
                .submit(10L, CONTEST_PUBLIC_ID);

        mockMvc.perform(post(
                        "/api/contests/{publicId}/submission/submit",
                        CONTEST_PUBLIC_ID
                ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_SUBMISSION_STATUS_TRANSITION"));
    }

    private SubmissionRes response(SubmissionStatus status) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 24, 12, 0);
        return new SubmissionRes(
                "submission-public-id",
                "AI 캠퍼스",
                status,
                null,
                status == SubmissionStatus.DRAFT ? null : now,
                now.minusDays(1),
                now
        );
    }
}
