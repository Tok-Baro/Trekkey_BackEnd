package com.api.trekkey.domain.evidence.web.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.trekkey.domain.evidence.service.EvidenceAdminService;
import com.api.trekkey.domain.evidence.service.EvidenceSubmissionService;
import com.api.trekkey.global.config.SecurityConfig;
import com.api.trekkey.global.security.AuthPrincipal;
import com.api.trekkey.global.security.handler.JwtAccessDeniedHandler;
import com.api.trekkey.global.security.handler.JwtAuthenticationEntryPoint;
import com.api.trekkey.global.security.jwt.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {EvidenceSubmissionController.class, EvidenceAdminController.class}, properties = {
        "security.jwt.secret-key=c2VjdXJpdHktY29uZmlnLXRlc3Qtc2VjcmV0LW11c3QtYmUtNjQtYnl0ZXMtbG9uZy0xMjM0NTY3ODkwYWJjZGVm",
        "security.jwt.access-expiration=3600", "security.jwt.refresh-expiration=86400"
})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtExtractor.class, JwtTokenProvider.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class EvidenceControllerSecurityTest {
    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider tokenProvider;
    @MockitoBean EvidenceSubmissionService submissionService;
    @MockitoBean EvidenceAdminService adminService;
    @MockitoBean(name = "jpaMappingContext") JpaMetamodelMappingContext jpaMappingContext;

    @Test
    void frontendMultipartSubmissionRequiresParticipantAndDelegatesIdentityFromToken() throws Exception {
        MockMultipartFile request = new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE,
                """
                {"evidenceType":"QUALIFICATION","targetRecordType":"OTHER","title":"정보처리기사",
                 "issuerName":"한국산업인력공단","issuerCode":"HRDK_QNET","credentialNumber":"CERT-1234"}
                """.getBytes());
        MockMultipartFile file = new MockMultipartFile("file", "certificate.pdf", "application/pdf",
                "%PDF-1.7".getBytes());

        mockMvc.perform(multipart("/api/me/evidence-submissions").file(request).file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("PARTICIPANT")))
                .andExpect(status().isCreated());

        verify(submissionService).submit(eq(10L), any(), any());
    }

    @Test
    void anonymousCannotSubmitEvidence() throws Exception {
        mockMvc.perform(multipart("/api/me/evidence-submissions"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(submissionService);
    }

    @Test
    void participantCannotOpenAdminVerificationQueue() throws Exception {
        mockMvc.perform(get("/api/admin/evidence-verifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("PARTICIPANT")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(adminService);
    }

    @Test
    void adminCanOpenOwnOrganizationQueue() throws Exception {
        given(adminService.getQueue(10L, null)).willReturn(List.of());
        mockMvc.perform(get("/api/admin/evidence-verifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("ADMIN")))
                .andExpect(status().isOk());
        verify(adminService).getQueue(10L, null);
    }

    private String accessToken(String role) {
        AuthPrincipal principal = AuthPrincipal.of(10L, role.toLowerCase() + "@test.com", List.of(role));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return tokenProvider.createAccessToken(authentication);
    }
}
