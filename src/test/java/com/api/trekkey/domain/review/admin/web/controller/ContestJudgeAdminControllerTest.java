package com.api.trekkey.domain.review.admin.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.trekkey.domain.review.entity.ReviewLinkStatus;
import com.api.trekkey.domain.review.admin.service.ContestJudgeAdminService;
import com.api.trekkey.domain.review.admin.web.dto.request.ContestJudgeCreateReq;
import com.api.trekkey.domain.review.admin.web.dto.request.ReviewLinkIssueReq;
import com.api.trekkey.domain.review.admin.web.dto.response.ContestJudgeRes;
import com.api.trekkey.domain.review.admin.web.dto.response.ReviewLinkIssueRes;
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
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@ExtendWith(MockitoExtension.class)
class ContestJudgeAdminControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ContestJudgeAdminService contestJudgeAdminService;

    @BeforeEach
    void setUp() {
        ContestJudgeAdminController controller =
                new ContestJudgeAdminController(contestJudgeAdminService);
        AuthPrincipal principal =
                AuthPrincipal.of(10L, "admin@test.com", List.of("ADMIN"));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(authPrincipalResolver(principal))
                .build();
    }

    @Test
    @DisplayName("심사위원 등록에 성공하면 201과 링크 미발급 상태를 반환한다")
    void createJudge_returnsCreated() throws Exception {
        given(contestJudgeAdminService.createJudge(
                eq(10L),
                eq("contest-public-id"),
                any(ContestJudgeCreateReq.class)
        )).willReturn(judgeRes(200L, ReviewLinkStatus.NOT_ISSUED));

        mockMvc.perform(post("/api/admin/contests/{publicId}/judges", "contest-public-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "김심사",
                                  "roleLabel": "외부 전문가"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.message").value("심사위원을 등록했습니다."))
                .andExpect(jsonPath("$.data.id").value(200L))
                .andExpect(jsonPath("$.data.reviewLinkStatus").value("NOT_ISSUED"));
    }

    @Test
    @DisplayName("심사위원 이름이 공백이면 서비스 호출 없이 400을 반환한다")
    void createJudge_rejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/admin/contests/{publicId}/judges", "contest-public-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": " ",
                                  "roleLabel": "외부 전문가"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL_400_BODY"))
                .andExpect(jsonPath("$.data[0].field").value("name"));

        verifyNoInteractions(contestJudgeAdminService);
    }

    @Test
    @DisplayName("심사위원 목록에는 링크 상태만 반환한다")
    void getJudges_returnsList() throws Exception {
        given(contestJudgeAdminService.getJudges(10L, "contest-public-id"))
                .willReturn(List.of(judgeRes(200L, ReviewLinkStatus.ACTIVE)));

        mockMvc.perform(get("/api/admin/contests/{publicId}/judges", "contest-public-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(200L))
                .andExpect(jsonPath("$.data[0].reviewLinkStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data[0].reviewUrl").doesNotExist())
                .andExpect(jsonPath("$.data[0].reviewTokenHash").doesNotExist());
    }

    @Test
    @DisplayName("심사 링크 발급에 성공하면 원본 토큰이 포함된 fragment URL을 한 번 반환한다")
    void issueReviewLink_returnsCreatedWithUrl() throws Exception {
        given(contestJudgeAdminService.issueReviewLink(
                eq(10L),
                eq("contest-public-id"),
                eq(200L),
                any(ReviewLinkIssueReq.class)
        )).willReturn(new ReviewLinkIssueRes(
                200L,
                "https://trekkey.example.com/judge/review#token=raw-token",
                LocalDateTime.of(2099, 1, 1, 0, 0)
        ));

        mockMvc.perform(post(
                        "/api/admin/contests/{publicId}/judges/{judgeId}/review-link",
                        "contest-public-id",
                        200L
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiresAt\": \"2099-01-01T00:00:00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("심사 링크를 발급했습니다."))
                .andExpect(jsonPath("$.data.judgeId").value(200L))
                .andExpect(jsonPath("$.data.reviewUrl")
                        .value("https://trekkey.example.com/judge/review#token=raw-token"));
    }

    @Test
    @DisplayName("과거 만료 시각으로는 심사 링크를 발급할 수 없다")
    void issueReviewLink_rejectsPastExpiration() throws Exception {
        mockMvc.perform(post(
                        "/api/admin/contests/{publicId}/judges/{judgeId}/review-link",
                        "contest-public-id",
                        200L
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiresAt\": \"2020-01-01T00:00:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL_400_BODY"))
                .andExpect(jsonPath("$.data[0].field").value("expiresAt"));

        verifyNoInteractions(contestJudgeAdminService);
    }

    @Test
    @DisplayName("심사 링크 폐기에 성공하면 200을 반환한다")
    void revokeReviewLink_returnsOk() throws Exception {
        mockMvc.perform(delete(
                        "/api/admin/contests/{publicId}/judges/{judgeId}/review-link",
                        "contest-public-id",
                        200L
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("심사 링크를 폐기했습니다."));

        verify(contestJudgeAdminService)
                .revokeReviewLink(10L, "contest-public-id", 200L);
    }

    private HandlerMethodArgumentResolver authPrincipalResolver(AuthPrincipal principal) {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return AuthPrincipal.class.isAssignableFrom(parameter.getParameterType());
            }

            @Override
            public Object resolveArgument(
                    MethodParameter parameter,
                    ModelAndViewContainer mavContainer,
                    NativeWebRequest webRequest,
                    WebDataBinderFactory binderFactory) {
                return principal;
            }
        };
    }

    private ContestJudgeRes judgeRes(Long id, ReviewLinkStatus status) {
        LocalDateTime now = LocalDateTime.now();
        return new ContestJudgeRes(
                id,
                null,
                "김심사",
                "외부 전문가",
                status,
                status == ReviewLinkStatus.NOT_ISSUED ? null : now.minusHours(1),
                status == ReviewLinkStatus.NOT_ISSUED ? null : now.plusDays(1),
                null,
                now.minusDays(1),
                now
        );
    }
}
