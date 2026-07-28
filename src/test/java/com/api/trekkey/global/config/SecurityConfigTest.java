package com.api.trekkey.global.config;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.trekkey.domain.contest.publicapi.service.ContestService;
import com.api.trekkey.domain.contest.publicapi.web.controller.ContestController;
import com.api.trekkey.domain.contest.publicapi.web.dto.ContestLikeRes;
import com.api.trekkey.domain.contest.publicapi.web.dto.ContestSearchStatus;
import com.api.trekkey.domain.review.publicapi.service.ReviewAccessService;
import com.api.trekkey.domain.review.admin.service.ReviewAssignmentAdminService;
import com.api.trekkey.domain.review.publicapi.service.ReviewFileService;
import com.api.trekkey.domain.review.admin.service.ReviewRoundEntryAdminService;
import com.api.trekkey.domain.review.publicapi.service.ReviewSheetService;
import com.api.trekkey.domain.review.publicapi.service.ReviewSubmissionService;
import com.api.trekkey.domain.review.publicapi.web.controller.ReviewAccessController;
import com.api.trekkey.domain.review.admin.web.controller.ReviewAssignmentAdminController;
import com.api.trekkey.domain.review.publicapi.web.controller.ReviewFileController;
import com.api.trekkey.domain.review.admin.web.controller.ReviewRoundEntryAdminController;
import com.api.trekkey.domain.review.publicapi.web.controller.ReviewSheetController;
import com.api.trekkey.domain.review.publicapi.web.controller.ReviewSubmissionController;
import com.api.trekkey.domain.review.publicapi.web.dto.request.ReviewAccessReq;
import com.api.trekkey.domain.review.admin.web.dto.request.ReviewAssignmentPrepareReq;
import com.api.trekkey.domain.review.publicapi.web.dto.request.ReviewScoreReq;
import com.api.trekkey.domain.review.publicapi.web.dto.request.ReviewSubmitReq;
import com.api.trekkey.domain.submission.support.FileDownload;
import com.api.trekkey.domain.team.admin.service.TeamAdminService;
import com.api.trekkey.domain.team.admin.web.controller.TeamAdminController;
import com.api.trekkey.domain.team.admin.web.dto.TeamAdminListRes;
import com.api.trekkey.domain.team.publicapi.service.TeamApplicationService;
import com.api.trekkey.domain.team.publicapi.web.controller.ParticipantTeamController;
import com.api.trekkey.domain.team.publicapi.web.controller.TeamApplicationController;
import com.api.trekkey.domain.team.publicapi.web.dto.ApplicationProgressRes;
import com.api.trekkey.domain.team.publicapi.web.dto.TeamApplicationCreateReq;
import com.api.trekkey.domain.team.publicapi.web.dto.TeamApplicationUpdateReq;
import com.api.trekkey.global.security.AuthPrincipal;
import com.api.trekkey.global.security.handler.JwtAccessDeniedHandler;
import com.api.trekkey.global.security.handler.JwtAuthenticationEntryPoint;
import com.api.trekkey.global.security.jwt.JwtAuthenticationFilter;
import com.api.trekkey.global.security.jwt.JwtExtractor;
import com.api.trekkey.global.security.jwt.JwtTokenProvider;
import java.io.ByteArrayInputStream;
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

@WebMvcTest(
        controllers = {
                ContestController.class,
                TeamApplicationController.class,
                ParticipantTeamController.class,
                TeamAdminController.class,
                ReviewAccessController.class,
                ReviewAssignmentAdminController.class,
                ReviewFileController.class,
                ReviewRoundEntryAdminController.class,
                ReviewSheetController.class,
                ReviewSubmissionController.class
        },
        properties = "security.jwt.secret-key="
                + "c2VjdXJpdHktY29uZmlnLXRlc3Qtc2VjcmV0LW11c3QtYmUtNjQtYnl0ZXMtbG9uZy0xMjM0NTY3ODkwYWJjZGVm")
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
    private ReviewFileService reviewFileService;

    @MockitoBean
    private ReviewAssignmentAdminService reviewAssignmentAdminService;

    @MockitoBean
    private ReviewRoundEntryAdminService reviewRoundEntryAdminService;

    @MockitoBean
    private ReviewSubmissionService reviewSubmissionService;

    @MockitoBean
    private TeamAdminService teamAdminService;

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
    @DisplayName("대회 좋아요는 인증 없이 요청할 수 없다")
    void contestLike_rejectsAnonymous() throws Exception {
        mockMvc.perform(post("/api/contests/{publicId}/like", "public-id"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GLOBAL_401"));

        verifyNoInteractions(contestService);
    }

    @Test
    @DisplayName("참가자는 대회 좋아요를 토글할 수 있다")
    void contestLike_permitsParticipant() throws Exception {
        String publicId = "public-id";
        given(contestService.toggleLike(10L, publicId))
                .willReturn(new ContestLikeRes(1L, true));

        mockMvc.perform(post("/api/contests/{publicId}/like", publicId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("PARTICIPANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").value(1))
                .andExpect(jsonPath("$.data.likedByMe").value(true));

        verify(contestService).toggleLike(10L, publicId);
    }

    @Test
    @DisplayName("관리자는 대회 좋아요를 토글할 수 없다")
    void contestLike_rejectsAdmin() throws Exception {
        mockMvc.perform(post("/api/contests/{publicId}/like", "public-id")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GLOBAL_403"));

        verifyNoInteractions(contestService);
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
                        List.of(11L, 12L),
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
    @DisplayName("심사 파일 다운로드 POST는 토큰 검증을 위해 인증 없이 진입할 수 있다")
    void reviewFileDownload_permitsAnonymousPost() throws Exception {
        String rawToken = "a".repeat(43);
        ReviewAccessReq request = new ReviewAccessReq(rawToken);
        byte[] content = "pdf".getBytes();
        given(reviewFileService.downloadFile(10L, request))
                .willReturn(new FileDownload(
                        "work.pdf",
                        "application/pdf",
                        content.length,
                        new ByteArrayInputStream(content)
                ));

        mockMvc.perform(post(
                                "/api/review/files/{fileId}/download",
                                10L
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawToken + "\"}"))
                .andExpect(status().isOk());

        verify(reviewFileService).downloadFile(10L, request);
    }

    @Test
    @DisplayName("심사 파일 다운로드 사전 확인 POST는 토큰 검증을 위해 인증 없이 진입할 수 있다")
    void reviewFileAccessCheck_permitsAnonymousPost() throws Exception {
        String rawToken = "a".repeat(43);
        ReviewAccessReq request = new ReviewAccessReq(rawToken);

        mockMvc.perform(post(
                                "/api/review/files/{fileId}/download/check",
                                10L
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawToken + "\"}"))
                .andExpect(status().isOk());

        verify(reviewFileService).validateFileAccess(10L, request);
    }

    @Test
    @DisplayName("심사 파일 다운로드 경로의 GET은 인증 없이 사용할 수 없다")
    void reviewFileDownloadGet_rejectsAnonymous() throws Exception {
        mockMvc.perform(get(
                        "/api/review/files/{fileId}/download",
                        10L
                ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GLOBAL_401"));

        verifyNoInteractions(reviewFileService);
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
                20L,
                null
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
                .prepareEntries(10L, "public-id", 20L, null);
    }

    @Test
    @DisplayName("최고 관리자는 역할 계층을 통해 심사 대상 준비 API를 사용할 수 있다")
    void reviewEntryPreparation_permitsRootAdmin() throws Exception {
        given(reviewRoundEntryAdminService.prepareEntries(
                10L,
                "public-id",
                20L,
                null
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
                .prepareEntries(10L, "public-id", 20L, null);
    }

    @Test
    @DisplayName("내 참가 신청 목록은 인증 없이 조회할 수 없다")
    void myApplications_rejectsAnonymous() throws Exception {
        mockMvc.perform(get("/api/me/applications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GLOBAL_401"));

        verifyNoInteractions(teamApplicationService);
    }

    @Test
    @DisplayName("참가자는 본인의 참가 신청 목록을 조회할 수 있다")
    void myApplications_permitsParticipant() throws Exception {
        given(teamApplicationService.getMyApplications(10L)).willReturn(List.of());

        mockMvc.perform(get("/api/me/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("PARTICIPANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());

        verify(teamApplicationService).getMyApplications(10L);
    }

    @Test
    @DisplayName("관리자는 참가자의 신청 목록을 조회할 수 없다")
    void myApplications_rejectsAdmin() throws Exception {
        mockMvc.perform(get("/api/me/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GLOBAL_403"));

        verifyNoInteractions(teamApplicationService);
    }

    @Test
    @DisplayName("내 참가 신청 진행 현황은 인증 없이 조회할 수 없다")
    void applicationProgress_rejectsAnonymous() throws Exception {
        mockMvc.perform(get(
                        "/api/me/applications/{contestPublicId}/progress",
                        "contest-public-id"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GLOBAL_401"));

        verifyNoInteractions(teamApplicationService);
    }

    @Test
    @DisplayName("참가자는 본인이 속한 신청의 진행 현황을 조회할 수 있다")
    void applicationProgress_permitsParticipant() throws Exception {
        given(teamApplicationService.getApplicationProgress(10L, "contest-public-id"))
                .willReturn(new ApplicationProgressRes("contest-public-id", List.of()));

        mockMvc.perform(get(
                                "/api/me/applications/{contestPublicId}/progress",
                                "contest-public-id")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("PARTICIPANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contestPublicId").value("contest-public-id"))
                .andExpect(jsonPath("$.data.steps").isArray());

        verify(teamApplicationService).getApplicationProgress(10L, "contest-public-id");
    }

    @Test
    @DisplayName("관리자는 참가자의 신청 진행 현황을 조회할 수 없다")
    void applicationProgress_rejectsAdmin() throws Exception {
        mockMvc.perform(get(
                                "/api/me/applications/{contestPublicId}/progress",
                                "contest-public-id")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GLOBAL_403"));

        verifyNoInteractions(teamApplicationService);
    }

    @Test
    @DisplayName("내 참가 신청 수정은 인증 없이 요청할 수 없다")
    void updateApplication_rejectsAnonymous() throws Exception {
        mockMvc.perform(patch("/api/me/applications/{contestPublicId}", "contest-public-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validApplicationRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GLOBAL_401"));

        verifyNoInteractions(teamApplicationService);
    }

    @Test
    @DisplayName("참가자는 본인의 참가 신청을 수정할 수 있다")
    void updateApplication_permitsParticipant() throws Exception {
        mockMvc.perform(patch("/api/me/applications/{contestPublicId}", "contest-public-id")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("PARTICIPANT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validApplicationRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS_200"));

        verify(teamApplicationService).updateApplication(
                10L,
                "contest-public-id",
                new TeamApplicationUpdateReq(
                        "트랙키 팀",
                        "홍길동",
                        "컴퓨터공학부",
                        List.of(11L, 12L),
                        "hong@example.com",
                        "010-1234-5678",
                        "AI 아이디어를 구현하고 싶습니다."));
    }

    @Test
    @DisplayName("관리자는 참가자의 신청을 수정할 수 없다")
    void updateApplication_rejectsAdmin() throws Exception {
        mockMvc.perform(patch("/api/me/applications/{contestPublicId}", "contest-public-id")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validApplicationRequest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GLOBAL_403"));

        verifyNoInteractions(teamApplicationService);
    }

    @Test
    @DisplayName("내 팀 목록은 인증 없이 조회할 수 없다")
    void myTeams_rejectsAnonymous() throws Exception {
        mockMvc.perform(get("/api/me/teams"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GLOBAL_401"));

        verifyNoInteractions(teamApplicationService);
    }

    @Test
    @DisplayName("참가자는 본인이 속한 팀 목록을 조회할 수 있다")
    void myTeams_permitsParticipant() throws Exception {
        given(teamApplicationService.getMyTeams(10L)).willReturn(List.of());

        mockMvc.perform(get("/api/me/teams")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("PARTICIPANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());

        verify(teamApplicationService).getMyTeams(10L);
    }

    @Test
    @DisplayName("관리자는 참가자의 팀 목록을 조회할 수 없다")
    void myTeams_rejectsAdmin() throws Exception {
        mockMvc.perform(get("/api/me/teams")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GLOBAL_403"));

        verifyNoInteractions(teamApplicationService);
    }

    @Test
    @DisplayName("참가자 검색은 인증 없이 요청할 수 없다")
    void participantSearch_rejectsAnonymous() throws Exception {
        mockMvc.perform(get("/api/participants/search")
                        .param("keyword", "김"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GLOBAL_401"));

        verifyNoInteractions(teamApplicationService);
    }

    @Test
    @DisplayName("참가자는 같은 학교의 참가자를 검색할 수 있다")
    void participantSearch_permitsParticipant() throws Exception {
        given(teamApplicationService.searchParticipants(10L, "김")).willReturn(List.of());

        mockMvc.perform(get("/api/participants/search")
                        .param("keyword", "김")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("PARTICIPANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());

        verify(teamApplicationService).searchParticipants(10L, "김");
    }

    @Test
    @DisplayName("관리자는 참가자를 검색할 수 없다")
    void participantSearch_rejectsAdmin() throws Exception {
        mockMvc.perform(get("/api/participants/search")
                        .param("keyword", "김")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GLOBAL_403"));

        verifyNoInteractions(teamApplicationService);
    }

    @Test
    @DisplayName("참가자는 관리자 신청 목록에 접근할 수 없다")
    void adminTeamList_rejectsParticipant() throws Exception {
        mockMvc.perform(get("/api/admin/contests/{contestPublicId}/teams", "contest-public-id")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("PARTICIPANT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GLOBAL_403"));

        verifyNoInteractions(teamAdminService);
    }

    @Test
    @DisplayName("관리자는 관리자 신청 목록에 접근할 수 있다")
    void adminTeamList_permitsAdmin() throws Exception {
        given(teamAdminService.getTeams(10L, "contest-public-id", null))
                .willReturn(new TeamAdminListRes(List.of(), java.util.Map.of(), 0));

        mockMvc.perform(get("/api/admin/contests/{contestPublicId}/teams", "contest-public-id")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content").isEmpty());

        verify(teamAdminService).getTeams(10L, "contest-public-id", null);
    }

    private String validApplicationRequest() {
        return """
                {
                  "teamName": "트랙키 팀",
                  "leaderName": "홍길동",
                  "major": "컴퓨터공학부",
                  "memberUserIds": [11, 12],
                  "contactEmail": "hong@example.com",
                  "phone": "010-1234-5678",
                  "motivation": "AI 아이디어를 구현하고 싶습니다."
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
