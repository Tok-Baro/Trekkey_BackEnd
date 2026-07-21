package com.api.trekkey.global.config;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.trekkey.domain.contest.service.ContestService;
import com.api.trekkey.domain.contest.web.controller.ContestController;
import com.api.trekkey.domain.contest.web.dto.ContestSearchStatus;
import com.api.trekkey.global.security.AuthPrincipal;
import com.api.trekkey.global.security.handler.JwtAccessDeniedHandler;
import com.api.trekkey.global.security.handler.JwtAuthenticationEntryPoint;
import com.api.trekkey.global.security.jwt.JwtAuthenticationFilter;
import com.api.trekkey.global.security.jwt.JwtExtractor;
import com.api.trekkey.global.security.jwt.JwtTokenProvider;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ContestController.class)
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

    @MockitoBean(name = "jpaMappingContext")
    private JpaMetamodelMappingContext jpaMappingContext;

    @Test
    @DisplayName("대회 상세는 인증 없이 조회할 수 있다")
    void contestDetail_permitsAnonymous() throws Exception {
        String publicId = "f04739b5-bb66-4c3f-bf91-31b8712011be";
        given(contestService.getContestDetail(publicId)).willReturn(null);

        mockMvc.perform(get("/api/contests/{publicId}", publicId))
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

    private String accessToken(String role) {
        AuthPrincipal principal = AuthPrincipal.of(10L, "participant@example.com", List.of(role));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return jwtTokenProvider.createAccessToken(authentication);
    }

}
