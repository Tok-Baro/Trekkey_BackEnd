package com.api.trekkey.global.config;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.trekkey.domain.contest.publicapi.service.ContestService;
import com.api.trekkey.domain.contest.publicapi.web.controller.ContestController;
import com.api.trekkey.domain.contest.publicapi.web.dto.ContestSearchStatus;
import com.api.trekkey.domain.review.service.ReviewAccessService;
import com.api.trekkey.domain.review.service.ReviewAssignmentAdminService;
import com.api.trekkey.domain.review.service.ReviewRoundEntryAdminService;
import com.api.trekkey.domain.review.service.ReviewSheetService;
import com.api.trekkey.domain.review.service.ReviewSubmissionService;
import com.api.trekkey.domain.review.web.controller.ReviewAccessController;
import com.api.trekkey.domain.review.web.controller.ReviewAssignmentAdminController;
import com.api.trekkey.domain.review.web.controller.ReviewRoundEntryAdminController;
import com.api.trekkey.domain.review.web.controller.ReviewSheetController;
import com.api.trekkey.domain.review.web.controller.ReviewSubmissionController;
import com.api.trekkey.domain.review.web.dto.request.ReviewAccessReq;
import com.api.trekkey.domain.review.web.dto.request.ReviewAssignmentPrepareReq;
import com.api.trekkey.domain.review.web.dto.request.ReviewScoreReq;
import com.api.trekkey.domain.review.web.dto.request.ReviewSubmitReq;
import com.api.trekkey.domain.submission.publicapi.service.SubmissionService;
import com.api.trekkey.domain.submission.publicapi.web.controller.SubmissionController;
import com.api.trekkey.domain.submission.publicapi.web.dto.SubmissionSaveReq;
import com.api.trekkey.domain.team.publicapi.service.TeamApplicationService;
import com.api.trekkey.domain.team.publicapi.web.controller.TeamApplicationController;
import com.api.trekkey.domain.team.publicapi.web.dto.TeamApplicationCreateReq;
import com.api.trekkey.global.security.AuthPrincipal;
import com.api.trekkey.global.security.handler.JwtAccessDeniedHandler;
import com.api.trekkey.global.security.handler.JwtAuthenticationEntryPoint;
import com.api.trekkey.global.security.jwt.JwtAuthenticationFilter;
import com.api.trekkey.global.security.jwt.JwtExtractor;
import com.api.trekkey.global.security.jwt.JwtTokenProvider;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({
        ContestController.class,
        TeamApplicationController.class,
        ReviewAccessController.class,
        ReviewAssignmentAdminController.class,
        ReviewRoundEntryAdminController.class,
        ReviewSheetController.class,
        ReviewSubmissionController.class,
        SubmissionController.class
})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtExtractor.class,
        JwtTokenProvider.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class
})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private ContestService contestService;

    @MockitoBean
    private TeamApplicationService teamApplicationService;

    @MockitoBean
    private ReviewAccessService reviewAccessService;

    @MockitoBean
    private ReviewSheetService reviewSheetService;

    @MockitoBean
    private ReviewAssignmentAdminService reviewAssignmentAdminService;

    @MockitoBean
    private ReviewRoundEntryAdminService reviewRoundEntryAdminService;

    @MockitoBean
    private ReviewSubmissionService reviewSubmissionService;

    @MockitoBean
    private SubmissionService submissionService;

    @MockitoBean(name = "jpaMappingContext")
    private JpaMetamodelMappingContext jpaMappingContext;

    @Test
    @DisplayName("대회 상세는 인증 없이 조회할 수 없다")
    void contestDetail_rejectsAnonymous() throws Exception {
        String publicId = "f04739b5-bb66-4c3f-bf91-31b8712011be";

        mockMvc.perform(get("/api/contests/{publicId}", publicId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GLOBAL_401"));

        verifyNoInteractions(contestService);
    }

    @Test
    @DisplayName("참가자는 대회 상세를 조회할 수 있다")
    void contestDetail_permitsParticipant() throws Exception {
        String publicId = "f04739b5-bb66-4c3f-bf91-31b8712011be";
        given(contestService.getContestDetail(10L, publicId)).willReturn(null);

        mockMvc.perform(get("/api/contests/{publicId}", publicId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("PARTICIPANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));
    }

    @Test
    @DisplayName("대회 목록은 인증 없이 조회할 수 없다")
    void contestList_rejectsAnonymous() throws Exception {
        mockMvc.perform(get("/api/contests"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GLOBAL_401"));

        verifyNoInteractions(contestService);
    }

    @Test
    @DisplayName("참가자는 대회 목록을 조회할 수 있다")
    void contestList_permitsParticipant() throws Exception {
        given(contestService.searchContests(10L, null, ContestSearchStatus.OPEN))
                .willReturn(List.of());

        mockMvc.perform(get("/api/contests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("PARTICIPANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("참가 신청은 인증 없이 요청할 수 없다")
    void contestApplication_rejectsAnonymous() throws Exception {
        mockMvc.perform(post("/api/contests/{publicId}/applications", "public-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validApplicationRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GLOBAL_401"));

        verifyNoInteractions(teamApplicationService);
    }

    @Test
    @DisplayName("참가자는 대회에 참가 신청할 수 있다")
    void contestApplication_permitsParticipant() throws Exception {
        String publicId = "public-id";

        mockMvc.perform(post("/api/contests/{publicId}/applications", publicId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("PARTICIPANT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validApplicationRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS_201"));

        verify(teamApplicationService).createApplication(
                10L,
                publicId,
                new TeamApplicationCreateReq(
                        "트랙키 팀",
                        "홍길동",
                        "컴퓨터공학부",
                        3,
                        "hong@example.com",
                        "010-1234-5678",
                        "AI 아이디어를 구현하고 싶습니다."));
    }

    @Test
    @DisplayName("관리자는 참가 신청할 수 없다")
    void contestApplication_rejectsAdmin() throws Exception {
        mockMvc.perform(post("/api/contests/{publicId}/applications", "public-id")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validApplicationRequest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GLOBAL_403"));

        verifyNoInteractions(teamApplicationService);
    }

    @Test
    @DisplayName("심사 링크 확인 진입점은 인증 없이 요청할 수 있다")
    void reviewAccess_permitsAnonymous() throws Exception {
        String rawToken = "a".repeat(43);

        mockMvc.perform(post("/api/review/access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS_200"));

        verify(reviewAccessService)
                .verifyAccess(new ReviewAccessReq(rawToken));
    }

    @Test
    @DisplayName("심사 평가표 POST는 토큰 검증을 위해 인증 없이 진입할 수 있다")
    void reviewAssignments_permitsAnonymousPost() throws Exception {
        String rawToken = "a".repeat(43);
        given(reviewSheetService.getReviewSheet(
                new ReviewAccessReq(rawToken)
        )).willReturn(null);

        mockMvc.perform(post("/api/review/assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS_200"));

        verify(reviewSheetService)
                .getReviewSheet(new ReviewAccessReq(rawToken));
    }

    @Test
    @DisplayName("심사 평가표의 GET 요청은 인증 없이 사용할 수 없다")
    void reviewAssignmentsGet_rejectsAnonymous() throws Exception {
        mockMvc.perform(get("/api/review/assignments"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GLOBAL_401"));

        verifyNoInteractions(reviewSheetService);
    }

    @Test
    @DisplayName("채점 제출 PUT은 토큰 검증을 위해 인증 없이 진입할 수 있다")
    void reviewSubmission_permitsAnonymousPut() throws Exception {
        ReviewSubmitReq request = validReviewSubmissionRequest();
        given(reviewSubmissionService.submitReview(
                500L,
                request
        )).willReturn(null);

        mockMvc.perform(put(
                        "/api/review/assignments/{assignmentId}/review",
                        500L
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validReviewSubmissionRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS_200"));

        verify(reviewSubmissionService).submitReview(500L, request);
    }

    @Test
    @DisplayName("채점 제출 경로의 GET은 인증 없이 사용할 수 없다")
    void reviewSubmissionGet_rejectsAnonymous() throws Exception {
        mockMvc.perform(get(
                        "/api/review/assignments/{assignmentId}/review",
                        500L
                ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GLOBAL_401"));

        verifyNoInteractions(reviewSubmissionService);
    }

    @Test
    @DisplayName("채점 제출 경로의 POST는 인증 없이 사용할 수 없다")
    void reviewSubmissionPost_rejectsAnonymous() throws Exception {
        mockMvc.perform(post(
                        "/api/review/assignments/{assignmentId}/review",
                        500L
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validReviewSubmissionRequestJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GLOBAL_401"));

        verifyNoInteractions(reviewSubmissionService);
    }

    @Test
    @DisplayName("채점 제출과 유사한 하위 PUT 경로는 인증 없이 사용할 수 없다")
    void reviewSubmissionNestedPath_rejectsAnonymous() throws Exception {
        mockMvc.perform(put(
                        "/api/review/assignments/{assignmentId}/review/draft",
                        500L
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validReviewSubmissionRequestJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GLOBAL_401"));

        verifyNoInteractions(reviewSubmissionService);
    }

    @Test
    @DisplayName("심사위원 배정 API는 인증 없이 요청할 수 없다")
    void reviewAssignmentPreparation_rejectsAnonymous() throws Exception {
        mockMvc.perform(post(
                        "/api/admin/contests/{publicId}/review-rounds/{roundId}"
                                + "/judges/{judgeId}/assignments/prepare",
                        "public-id",
                        20L,
                        30L
                ).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GLOBAL_401"));

        verifyNoInteractions(reviewAssignmentAdminService);
    }

    @Test
    @DisplayName("참가자는 심사위원 배정 API를 사용할 수 없다")
    void reviewAssignmentPreparation_rejectsParticipant() throws Exception {
        mockMvc.perform(post(
                        "/api/admin/contests/{publicId}/review-rounds/{roundId}"
                                + "/judges/{judgeId}/assignments/prepare",
                        "public-id",
                        20L,
                        30L
                ).header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken("PARTICIPANT")
                ).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GLOBAL_403"));

        verifyNoInteractions(reviewAssignmentAdminService);
    }

    @Test
    @DisplayName("관리자는 심사위원 평가표를 준비할 수 있다")
    void reviewAssignmentPreparation_permitsAdmin() throws Exception {
        ReviewAssignmentPrepareReq req =
                new ReviewAssignmentPrepareReq(null);
        given(reviewAssignmentAdminService.prepareAssignments(
                10L,
                "public-id",
                20L,
                30L,
                req
        )).willReturn(List.of());

        mockMvc.perform(post(
                        "/api/admin/contests/{publicId}/review-rounds/{roundId}"
                                + "/judges/{judgeId}/assignments/prepare",
                        "public-id",
                        20L,
                        30L
                ).header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken("ADMIN")
                ).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS_200"));

        verify(reviewAssignmentAdminService).prepareAssignments(
                10L,
                "public-id",
                20L,
                30L,
                req
        );
    }

    @Test
    @DisplayName("최고 관리자는 역할 계층을 통해 심사위원 평가표를 준비할 수 있다")
    void reviewAssignmentPreparation_permitsRootAdmin() throws Exception {
        ReviewAssignmentPrepareReq req =
                new ReviewAssignmentPrepareReq(null);
        given(reviewAssignmentAdminService.prepareAssignments(
                10L,
                "public-id",
                20L,
                30L,
                req
        )).willReturn(List.of());

        mockMvc.perform(post(
                        "/api/admin/contests/{publicId}/review-rounds/{roundId}"
                                + "/judges/{judgeId}/assignments/prepare",
                        "public-id",
                        20L,
                        30L
                ).header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken("ROOT_ADMIN")
                ).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS_200"));

        verify(reviewAssignmentAdminService).prepareAssignments(
                10L,
                "public-id",
                20L,
                30L,
                req
        );
    }

    @Test
    @DisplayName("심사 대상 준비 API는 인증 없이 요청할 수 없다")
    void reviewEntryPreparation_rejectsAnonymous() throws Exception {
        mockMvc.perform(post(
                        "/api/admin/contests/{publicId}/review-rounds/{roundId}/entries/prepare",
                        "public-id",
                        20L
                ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GLOBAL_401"));

        verifyNoInteractions(reviewRoundEntryAdminService);
    }

    @Test
    @DisplayName("참가자는 심사 대상 준비 API를 사용할 수 없다")
    void reviewEntryPreparation_rejectsParticipant() throws Exception {
        mockMvc.perform(post(
                        "/api/admin/contests/{publicId}/review-rounds/{roundId}/entries/prepare",
                        "public-id",
                        20L
                ).header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken("PARTICIPANT")
                ))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GLOBAL_403"));

        verifyNoInteractions(reviewRoundEntryAdminService);
    }

    @Test
    @DisplayName("관리자는 심사 대상 준비 API를 사용할 수 있다")
    void reviewEntryPreparation_permitsAdmin() throws Exception {
        given(reviewRoundEntryAdminService.prepareEntries(
                10L,
                "public-id",
                20L
        )).willReturn(List.of());

        mockMvc.perform(post(
                        "/api/admin/contests/{publicId}/review-rounds/{roundId}/entries/prepare",
                        "public-id",
                        20L
                ).header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken("ADMIN")
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS_200"));

        verify(reviewRoundEntryAdminService)
                .prepareEntries(10L, "public-id", 20L);
    }

    @Test
    @DisplayName("최고 관리자는 역할 계층을 통해 심사 대상 준비 API를 사용할 수 있다")
    void reviewEntryPreparation_permitsRootAdmin() throws Exception {
        given(reviewRoundEntryAdminService.prepareEntries(
                10L,
                "public-id",
                20L
        )).willReturn(List.of());

        mockMvc.perform(post(
                        "/api/admin/contests/{publicId}/review-rounds/{roundId}/entries/prepare",
                        "public-id",
                        20L
                ).header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken("ROOT_ADMIN")
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS_200"));

        verify(reviewRoundEntryAdminService)
                .prepareEntries(10L, "public-id", 20L);
    }

    @Test
    @DisplayName("제출물 API는 인증 없이 요청할 수 없다")
    void submission_rejectsAnonymous() throws Exception {
        mockMvc.perform(put("/api/contests/{publicId}/submission", "public-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSubmissionRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GLOBAL_401"));

        verifyNoInteractions(submissionService);
    }

    @Test
    @DisplayName("제출물 하위 작업 경로도 인증 없이 요청할 수 없다")
    void submissionAction_rejectsAnonymous() throws Exception {
        mockMvc.perform(post(
                        "/api/contests/{publicId}/submission/submit",
                        "public-id"
                ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GLOBAL_401"));

        verifyNoInteractions(submissionService);
    }

    @Test
    @DisplayName("참가자는 자신의 제출물 초안을 저장할 수 있다")
    void submission_permitsParticipant() throws Exception {
        mockMvc.perform(put("/api/contests/{publicId}/submission", "public-id")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + accessToken("PARTICIPANT")
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSubmissionRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS_200"));

        verify(submissionService).saveDraft(
                10L,
                "public-id",
                new SubmissionSaveReq("AI 캠퍼스")
        );
    }

    @Test
    @DisplayName("관리자는 참가자 제출물 API를 사용할 수 없다")
    void submission_rejectsAdmin() throws Exception {
        mockMvc.perform(put("/api/contests/{publicId}/submission", "public-id")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + accessToken("ADMIN")
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSubmissionRequest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GLOBAL_403"));

        verifyNoInteractions(submissionService);
    }

    private String validApplicationRequest() {
        return """
                {
                  "teamName": "트랙키 팀",
                  "leaderName": "홍길동",
                  "major": "컴퓨터공학부",
                  "memberCount": 3,
                  "contactEmail": "hong@example.com",
                  "phone": "010-1234-5678",
                  "motivation": "AI 아이디어를 구현하고 싶습니다."
                }
                """;
    }

    private String validSubmissionRequest() {
        return """
                {
                  "title": "AI 캠퍼스"
                }
                """;
    }

    private ReviewSubmitReq validReviewSubmissionRequest() {
        return new ReviewSubmitReq(
                "a".repeat(43),
                List.of(new ReviewScoreReq(
                        600L,
                        new BigDecimal("10")
                )),
                "의견"
        );
    }

    private String validReviewSubmissionRequestJson() {
        return """
                {
                  "token": "%s",
                  "scores": [
                    {
                      "criterionId": 600,
                      "score": 10
                    }
                  ],
                  "comment": "의견"
                }
                """.formatted("a".repeat(43));
    }

    private String accessToken(String role) {
        AuthPrincipal principal = AuthPrincipal.of(10L, "participant@example.com", List.of(role));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return jwtTokenProvider.createAccessToken(authentication);
    }

}
