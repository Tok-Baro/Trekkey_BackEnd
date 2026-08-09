package com.api.trekkey.domain.award.admin.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.trekkey.domain.award.admin.service.AwardAdminService;
import com.api.trekkey.domain.award.admin.web.dto.AwardCandidateUpdateReq;
import com.api.trekkey.domain.award.admin.web.dto.AwardRes;
import com.api.trekkey.domain.award.entity.AwardStatus;
import com.api.trekkey.domain.award.entity.AwardType;
import com.api.trekkey.global.exception.GlobalExceptionHandler;
import com.api.trekkey.global.security.AuthPrincipal;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class AwardAdminControllerTest {

    @Mock
    private AwardAdminService awardAdminService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AwardAdminController controller =
                new AwardAdminController(awardAdminService);
        AuthPrincipal principal = AuthPrincipal.of(
                10L, "admin@test.com", List.of("ADMIN"));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        authPrincipalResolver(principal))
                .build();
    }

    @Test
    @DisplayName("수상 후보를 총장상과 보류 상태로 변경한다")
    void updateCandidate_updatesPrizeAndStatus() throws Exception {
        given(awardAdminService.updateCandidate(
                eq(10L),
                eq("award-public-id"),
                org.mockito.ArgumentMatchers.any(
                        AwardCandidateUpdateReq.class)))
                .willReturn(new AwardRes(
                        "award-public-id",
                        "contest-public-id",
                        "team-public-id",
                        1,
                        "총장상",
                        AwardType.PRESIDENT_AWARD,
                        "트레키 팀",
                        "AI 작품",
                        new BigDecimal("95.00"),
                        AwardStatus.HELD,
                        "2026-C725050E0-001",
                        null));

        mockMvc.perform(patch(
                        "/api/admin/awards/{awardPublicId}",
                        "award-public-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "awardType": "PRESIDENT_AWARD",
                                  "status": "HELD"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.prize")
                        .value("총장상"))
                .andExpect(jsonPath("$.data.awardType")
                        .value("PRESIDENT_AWARD"))
                .andExpect(jsonPath("$.data.status")
                        .value("HELD"));

        ArgumentCaptor<AwardCandidateUpdateReq> captor =
                ArgumentCaptor.forClass(
                        AwardCandidateUpdateReq.class);
        verify(awardAdminService).updateCandidate(
                eq(10L), eq("award-public-id"), captor.capture());
        assertThat(captor.getValue().awardType())
                .isEqualTo(AwardType.PRESIDENT_AWARD);
        assertThat(captor.getValue().status())
                .isEqualTo(AwardStatus.HELD);
    }

    @Test
    @DisplayName("수상 후보 상태가 누락된 요청은 거부한다")
    void updateCandidate_rejectsMissingStatus() throws Exception {
        mockMvc.perform(patch(
                        "/api/admin/awards/{awardPublicId}",
                        "award-public-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "awardType": "SPECIAL"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(awardAdminService);
    }

    private HandlerMethodArgumentResolver authPrincipalResolver(
            AuthPrincipal principal
    ) {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(
                    MethodParameter parameter
            ) {
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
}
