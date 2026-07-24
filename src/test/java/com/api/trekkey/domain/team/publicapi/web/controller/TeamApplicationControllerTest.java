package com.api.trekkey.domain.team.publicapi.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.trekkey.domain.team.exception.TeamErrorResponseCode;
import com.api.trekkey.domain.team.publicapi.service.TeamApplicationService;
import com.api.trekkey.domain.team.publicapi.web.dto.TeamApplicationCreateReq;
import com.api.trekkey.global.exception.CustomException;
import com.api.trekkey.global.exception.GlobalExceptionHandler;
import com.api.trekkey.global.security.AuthPrincipal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class TeamApplicationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TeamApplicationService teamApplicationService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(participantAuthentication());
        TeamApplicationController controller = new TeamApplicationController(teamApplicationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("유효한 참가 신청을 접수하고 201을 반환한다")
    void createApplication_returnsCreatedResponse() throws Exception {
        String publicId = "f04739b5-bb66-4c3f-bf91-31b8712011be";

        mockMvc.perform(post("/api/contests/{publicId}/applications", publicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS_201"))
                .andExpect(jsonPath("$.httpStatus").value(201))
                .andExpect(jsonPath("$.message").value("참가 신청을 접수했습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        ArgumentCaptor<TeamApplicationCreateReq> requestCaptor =
                ArgumentCaptor.forClass(TeamApplicationCreateReq.class);
        verify(teamApplicationService).createApplication(eq(10L), eq(publicId), requestCaptor.capture());
        assertThat(requestCaptor.getValue()).isEqualTo(new TeamApplicationCreateReq(
                "트랙키 팀",
                "홍길동",
                "컴퓨터공학부",
                3,
                "hong@example.com",
                "010-1234-5678",
                "AI 아이디어를 구현하고 싶습니다."));
    }

    @Test
    @DisplayName("필수 신청 값이 잘못되면 400을 반환한다")
    void createApplication_returnsBadRequestWhenRequestIsInvalid() throws Exception {
        mockMvc.perform(post("/api/contests/{publicId}/applications", "public-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "teamName": "",
                                  "leaderName": "홍길동",
                                  "major": "컴퓨터공학부",
                                  "memberCount": 6,
                                  "contactEmail": "wrong-email",
                                  "phone": "",
                                  "motivation": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("GLOBAL_400_BODY"))
                .andExpect(jsonPath("$.httpStatus").value(400));

        verifyNoInteractions(teamApplicationService);
    }

    @Test
    @DisplayName("이미 신청한 대회면 409를 반환한다")
    void createApplication_returnsConflictWhenApplicationAlreadyExists() throws Exception {
        willThrow(new CustomException(TeamErrorResponseCode.TEAM_APPLICATION_ALREADY_EXISTS))
                .given(teamApplicationService)
                .createApplication(any(Long.class), any(String.class), any(TeamApplicationCreateReq.class));

        mockMvc.perform(post("/api/contests/{publicId}/applications", "public-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("TEAM_APPLICATION_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.httpStatus").value(409));
    }

    private String validRequest() {
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

    private Authentication participantAuthentication() {
        AuthPrincipal principal = AuthPrincipal.of(10L, "participant@example.com", List.of("PARTICIPANT"));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
