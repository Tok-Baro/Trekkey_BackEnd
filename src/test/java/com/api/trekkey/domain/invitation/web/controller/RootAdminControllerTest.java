package com.api.trekkey.domain.invitation.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.trekkey.domain.invitation.entity.InvitationStatus;
import com.api.trekkey.domain.invitation.exception.AdminInvitationErrorResponseCode;
import com.api.trekkey.domain.invitation.service.AdminInvitationService;
import com.api.trekkey.domain.invitation.web.dto.request.AdminApprovalReq;
import com.api.trekkey.domain.invitation.web.dto.request.AdminInvitationCreateReq;
import com.api.trekkey.domain.invitation.web.dto.response.AdminInvitationRes;
import com.api.trekkey.domain.invitation.web.dto.response.AdminPendingRes;
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
class RootAdminControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AdminInvitationService adminInvitationService;

    @BeforeEach
    void setUp() {
        RootAdminController rootAdminController = new RootAdminController(adminInvitationService);
        AuthPrincipal principal = AuthPrincipal.of(1L, "root@test.com", List.of("ROOT_ADMIN"));
        mockMvc = MockMvcBuilders.standaloneSetup(rootAdminController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(authPrincipalResolver(principal))
                .build();
    }

    @Test
    @DisplayName("초대 발급에 성공하면 201과 발급 메시지, 초대 URL을 반환한다")
    void createInvitation_returnsCreatedWithInviteUrl() throws Exception {
        given(adminInvitationService.createInvitation(eq(1L), any(AdminInvitationCreateReq.class)))
                .willReturn(invitationRes(100L, "staff@test.com",
                        "https://trekkey.example.com/signup/admin?token=raw-token"));

        mockMvc.perform(post("/api/root/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"staff@test.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS_201"))
                .andExpect(jsonPath("$.httpStatus").value(201))
                .andExpect(jsonPath("$.message").value("관리자 초대를 발급했습니다."))
                .andExpect(jsonPath("$.data.id").value(100L))
                .andExpect(jsonPath("$.data.email").value("staff@test.com"))
                .andExpect(jsonPath("$.data.inviteUrl")
                        .value("https://trekkey.example.com/signup/admin?token=raw-token"));
    }

    @Test
    @DisplayName("초대 발급 시 이메일 형식이 잘못되면 400 GLOBAL_400_BODY를 반환한다")
    void createInvitation_returnsBadRequestWhenEmailIsInvalid() throws Exception {
        mockMvc.perform(post("/api/root/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("GLOBAL_400_BODY"))
                .andExpect(jsonPath("$.httpStatus").value(400))
                .andExpect(jsonPath("$.data[0].field").value("email"));

        verifyNoInteractions(adminInvitationService);
    }

    @Test
    @DisplayName("초대 목록 조회에 성공하면 200과 목록을 반환한다")
    void getInvitations_returnsOkWithList() throws Exception {
        given(adminInvitationService.getInvitations(1L))
                .willReturn(List.of(invitationRes(100L, "staff@test.com", null)));

        mockMvc.perform(get("/api/root/invitations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS_200"))
                .andExpect(jsonPath("$.httpStatus").value(200))
                .andExpect(jsonPath("$.data[0].id").value(100L))
                .andExpect(jsonPath("$.data[0].email").value("staff@test.com"))
                .andExpect(jsonPath("$.data[0].status").value("ISSUED"))
                .andExpect(jsonPath("$.data[0].inviteUrl").doesNotExist());
    }

    @Test
    @DisplayName("초대 철회에 성공하면 200과 철회 메시지를 반환한다")
    void revokeInvitation_returnsOk() throws Exception {
        mockMvc.perform(delete("/api/root/invitations/{invitationId}", 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS_200"))
                .andExpect(jsonPath("$.httpStatus").value(200))
                .andExpect(jsonPath("$.message").value("초대를 철회했습니다."));

        verify(adminInvitationService).revokeInvitation(1L, 100L);
    }

    @Test
    @DisplayName("승인 대기 관리자 목록 조회에 성공하면 200과 목록을 반환한다")
    void getPendingAdmins_returnsOkWithList() throws Exception {
        given(adminInvitationService.getPendingAdmins(1L))
                .willReturn(List.of(pendingRes(2L, "staff@test.com")));

        mockMvc.perform(get("/api/root/admin-approvals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS_200"))
                .andExpect(jsonPath("$.httpStatus").value(200))
                .andExpect(jsonPath("$.data[0].userId").value(2L))
                .andExpect(jsonPath("$.data[0].email").value("staff@test.com"));
    }

    @Test
    @DisplayName("관리자 가입 승인에 성공하면 200과 승인 메시지를 반환한다")
    void decideApproval_returnsOkWithApproveMessage() throws Exception {
        given(adminInvitationService.decideApproval(eq(1L), eq(2L), any(AdminApprovalReq.class)))
                .willReturn(pendingRes(2L, "staff@test.com"));

        mockMvc.perform(patch("/api/root/admin-approvals/{userId}", 2L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approve\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS_200"))
                .andExpect(jsonPath("$.httpStatus").value(200))
                .andExpect(jsonPath("$.message").value("관리자 가입을 승인했습니다."))
                .andExpect(jsonPath("$.data.userId").value(2L));
    }

    @Test
    @DisplayName("승인 대상 상태가 아니면 400 APPROVAL_TARGET_INVALID를 반환한다")
    void decideApproval_returnsBadRequestWhenTargetIsInvalid() throws Exception {
        willThrow(new CustomException(AdminInvitationErrorResponseCode.APPROVAL_TARGET_INVALID))
                .given(adminInvitationService)
                .decideApproval(eq(1L), eq(2L), any(AdminApprovalReq.class));

        mockMvc.perform(patch("/api/root/admin-approvals/{userId}", 2L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approve\": true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("APPROVAL_TARGET_INVALID"))
                .andExpect(jsonPath("$.httpStatus").value(400))
                .andExpect(jsonPath("$.message").value("승인 대상 상태가 아닙니다"));
    }

    //======= 헬퍼 메서드 ==========

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

    private AdminInvitationRes invitationRes(Long id, String email, String inviteUrl) {
        return new AdminInvitationRes(
                id,
                email,
                InvitationStatus.ISSUED,
                LocalDateTime.now().plusDays(7),
                LocalDateTime.now(),
                inviteUrl);
    }

    private AdminPendingRes pendingRes(Long userId, String email) {
        return new AdminPendingRes(
                userId,
                "김교직",
                email,
                "교무처",
                "주임",
                LocalDateTime.now().minusDays(1));
    }
}
