package com.api.trekkey.domain.review.admin.web.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.trekkey.domain.review.entity.ReviewAssignmentStatus;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.admin.service.ReviewAssignmentAdminService;
import com.api.trekkey.domain.review.admin.web.dto.request.ReviewAssignmentDueAtReq;
import com.api.trekkey.domain.review.admin.web.dto.request.ReviewAssignmentPrepareReq;
import com.api.trekkey.domain.review.admin.web.dto.response.ReviewAssignmentRes;
import com.api.trekkey.global.exception.CustomException;
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
class ReviewAssignmentAdminControllerTest {

    private static final String CONTEST_PUBLIC_ID = "contest-public-id";
    private static final Long REVIEW_ROUND_ID = 300L;
    private static final Long JUDGE_ID = 200L;
    private static final LocalDateTime DUE_AT =
            LocalDateTime.of(2099, 8, 1, 18, 0);

    private MockMvc mockMvc;

    @Mock
    private ReviewAssignmentAdminService reviewAssignmentAdminService;

    @BeforeEach
    void setUp() {
        ReviewAssignmentAdminController controller =
                new ReviewAssignmentAdminController(
                        reviewAssignmentAdminService);
        AuthPrincipal principal =
                AuthPrincipal.of(10L, "admin@test.com", List.of("ADMIN"));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(authPrincipalResolver(principal))
                .build();
    }

    @Test
    @DisplayName("평가표 준비 요청을 서비스에 전달하고 생성된 배정을 반환한다")
    void prepareAssignments_returnsOk() throws Exception {
        ReviewAssignmentPrepareReq request =
                new ReviewAssignmentPrepareReq(DUE_AT);
        given(reviewAssignmentAdminService.prepareAssignments(
                10L,
                CONTEST_PUBLIC_ID,
                REVIEW_ROUND_ID,
                JUDGE_ID,
                request
        )).willReturn(List.of(assignmentRes()));

        mockMvc.perform(post(
                        "/api/admin/contests/{publicId}/review-rounds/{roundId}"
                                + "/judges/{judgeId}/assignments/prepare",
                        CONTEST_PUBLIC_ID,
                        REVIEW_ROUND_ID,
                        JUDGE_ID
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dueAt\":\"2099-08-01T18:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.message")
                        .value("심사위원 평가표를 준비했습니다."))
                .andExpect(jsonPath("$.data[0].id").value(500L))
                .andExpect(jsonPath("$.data[0].judgeId").value(JUDGE_ID))
                .andExpect(jsonPath("$.data[0].judgeName").value("김심사"))
                .andExpect(jsonPath("$.data[0].reviewRoundId")
                        .value(REVIEW_ROUND_ID))
                .andExpect(jsonPath("$.data[0].reviewRoundEntryId")
                        .value(400L))
                .andExpect(jsonPath("$.data[0].submissionPublicId")
                        .value("submission-public-id"))
                .andExpect(jsonPath("$.data[0].submissionTitle")
                        .value("AI 작품"))
                .andExpect(jsonPath("$.data[0].status")
                        .value("ASSIGNED"))
                .andExpect(jsonPath("$.data[0].dueAt[0]").value(2099));

        verify(reviewAssignmentAdminService).prepareAssignments(
                10L,
                CONTEST_PUBLIC_ID,
                REVIEW_ROUND_ID,
                JUDGE_ID,
                request
        );
    }

    @Test
    @DisplayName("심사위원 배정 목록 조회 요청을 서비스에 전달한다")
    void getAssignments_returnsOk() throws Exception {
        given(reviewAssignmentAdminService.getAssignments(
                10L,
                CONTEST_PUBLIC_ID,
                REVIEW_ROUND_ID,
                JUDGE_ID
        )).willReturn(List.of(assignmentRes()));

        mockMvc.perform(get(
                        "/api/admin/contests/{publicId}/review-rounds/{roundId}"
                                + "/judges/{judgeId}/assignments",
                        CONTEST_PUBLIC_ID,
                        REVIEW_ROUND_ID,
                        JUDGE_ID
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data[0].id").value(500L))
                .andExpect(jsonPath("$.data[0].judgeId").value(JUDGE_ID))
                .andExpect(jsonPath("$.data[0].reviewRoundId")
                        .value(REVIEW_ROUND_ID))
                .andExpect(jsonPath("$.data[0].status")
                        .value("ASSIGNED"));

        verify(reviewAssignmentAdminService).getAssignments(
                10L,
                CONTEST_PUBLIC_ID,
                REVIEW_ROUND_ID,
                JUDGE_ID
        );
    }

    @Test
    @DisplayName("배정할 수 없는 단계이면 409를 반환한다")
    void prepareAssignments_returnsConflict() throws Exception {
        ReviewAssignmentPrepareReq request =
                new ReviewAssignmentPrepareReq(DUE_AT);
        given(reviewAssignmentAdminService.prepareAssignments(
                10L,
                CONTEST_PUBLIC_ID,
                REVIEW_ROUND_ID,
                JUDGE_ID,
                request
        )).willThrow(new CustomException(
                ReviewErrorResponseCode
                        .REVIEW_ASSIGNMENT_PREPARATION_NOT_ALLOWED));

        mockMvc.perform(post(
                        "/api/admin/contests/{publicId}/review-rounds/{roundId}"
                                + "/judges/{judgeId}/assignments/prepare",
                        CONTEST_PUBLIC_ID,
                        REVIEW_ROUND_ID,
                        JUDGE_ID
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dueAt\":\"2099-08-01T18:00:00\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code")
                        .value("REVIEW_ASSIGNMENT_PREPARATION_NOT_ALLOWED"))
                .andExpect(jsonPath("$.httpStatus").value(409));

        verify(reviewAssignmentAdminService).prepareAssignments(
                10L,
                CONTEST_PUBLIC_ID,
                REVIEW_ROUND_ID,
                JUDGE_ID,
                request
        );
    }

    @Test
    @DisplayName("개별 심사 배정 취소 요청을 서비스에 전달한다")
    void cancelAssignment_returnsOk() throws Exception {
        given(reviewAssignmentAdminService.cancelAssignment(
                10L,
                CONTEST_PUBLIC_ID,
                REVIEW_ROUND_ID,
                JUDGE_ID,
                500L
        )).willReturn(assignmentRes(
                ReviewAssignmentStatus.CANCELED,
                DUE_AT
        ));

        mockMvc.perform(delete(
                        "/api/admin/contests/{publicId}/review-rounds/{roundId}"
                                + "/judges/{judgeId}/assignments/{assignmentId}",
                        CONTEST_PUBLIC_ID,
                        REVIEW_ROUND_ID,
                        JUDGE_ID,
                        500L
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("심사 배정을 취소했습니다."))
                .andExpect(jsonPath("$.data.status").value("CANCELED"));

        verify(reviewAssignmentAdminService).cancelAssignment(
                10L,
                CONTEST_PUBLIC_ID,
                REVIEW_ROUND_ID,
                JUDGE_ID,
                500L
        );
    }

    @Test
    @DisplayName("취소된 배정의 재배정 요청과 마감 시각을 전달한다")
    void reassignAssignment_returnsOk() throws Exception {
        ReviewAssignmentDueAtReq request =
                new ReviewAssignmentDueAtReq(DUE_AT);
        given(reviewAssignmentAdminService.reassignAssignment(
                10L,
                CONTEST_PUBLIC_ID,
                REVIEW_ROUND_ID,
                JUDGE_ID,
                500L,
                request
        )).willReturn(assignmentRes(
                ReviewAssignmentStatus.ASSIGNED,
                DUE_AT
        ));

        mockMvc.perform(post(
                        "/api/admin/contests/{publicId}/review-rounds/{roundId}"
                                + "/judges/{judgeId}/assignments"
                                + "/{assignmentId}/reassign",
                        CONTEST_PUBLIC_ID,
                        REVIEW_ROUND_ID,
                        JUDGE_ID,
                        500L
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dueAt\":\"2099-08-01T18:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("심사 배정을 다시 배정했습니다."))
                .andExpect(jsonPath("$.data.status").value("ASSIGNED"));

        verify(reviewAssignmentAdminService).reassignAssignment(
                10L,
                CONTEST_PUBLIC_ID,
                REVIEW_ROUND_ID,
                JUDGE_ID,
                500L,
                request
        );
    }

    @Test
    @DisplayName("배정 마감 시각 변경 요청을 서비스에 전달한다")
    void updateDueAt_returnsOk() throws Exception {
        ReviewAssignmentDueAtReq request =
                new ReviewAssignmentDueAtReq(DUE_AT);
        given(reviewAssignmentAdminService.updateDueAt(
                10L,
                CONTEST_PUBLIC_ID,
                REVIEW_ROUND_ID,
                JUDGE_ID,
                500L,
                request
        )).willReturn(assignmentRes(
                ReviewAssignmentStatus.ASSIGNED,
                DUE_AT
        ));

        mockMvc.perform(patch(
                        "/api/admin/contests/{publicId}/review-rounds/{roundId}"
                                + "/judges/{judgeId}/assignments"
                                + "/{assignmentId}/due-at",
                        CONTEST_PUBLIC_ID,
                        REVIEW_ROUND_ID,
                        JUDGE_ID,
                        500L
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dueAt\":\"2099-08-01T18:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("심사 배정 마감 시각을 변경했습니다."))
                .andExpect(jsonPath("$.data.dueAt[0]").value(2099));

        verify(reviewAssignmentAdminService).updateDueAt(
                10L,
                CONTEST_PUBLIC_ID,
                REVIEW_ROUND_ID,
                JUDGE_ID,
                500L,
                request
        );
    }

    @Test
    @DisplayName("마감 시각 변경 요청에 dueAt이 없으면 400을 반환한다")
    void updateDueAt_rejectsMissingDeadline() throws Exception {
        mockMvc.perform(patch(
                        "/api/admin/contests/{publicId}/review-rounds/{roundId}"
                                + "/judges/{judgeId}/assignments"
                                + "/{assignmentId}/due-at",
                        CONTEST_PUBLIC_ID,
                        REVIEW_ROUND_ID,
                        JUDGE_ID,
                        500L
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL_400_BODY"))
                .andExpect(jsonPath("$.data[0].field").value("dueAt"));
    }

    private HandlerMethodArgumentResolver authPrincipalResolver(
            AuthPrincipal principal
    ) {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return AuthPrincipal.class.isAssignableFrom(
                        parameter.getParameterType());
            }

            @Override
            public Object resolveArgument(
                    MethodParameter parameter,
                    ModelAndViewContainer mavContainer,
                    NativeWebRequest webRequest,
                    WebDataBinderFactory binderFactory
            ) {
                return principal;
            }
        };
    }

    private ReviewAssignmentRes assignmentRes() {
        return assignmentRes(
                ReviewAssignmentStatus.ASSIGNED,
                DUE_AT
        );
    }

    private ReviewAssignmentRes assignmentRes(
            ReviewAssignmentStatus status,
            LocalDateTime dueAt
    ) {
        return new ReviewAssignmentRes(
                500L,
                JUDGE_ID,
                "김심사",
                REVIEW_ROUND_ID,
                400L,
                "submission-public-id",
                "AI 작품",
                status,
                LocalDateTime.of(2026, 7, 24, 10, 0),
                dueAt,
                status == ReviewAssignmentStatus.COMPLETED
                        ? LocalDateTime.of(2026, 7, 24, 11, 0)
                        : null,
                LocalDateTime.of(2026, 7, 24, 10, 0),
                LocalDateTime.of(2026, 7, 24, 10, 0)
        );
    }
}
