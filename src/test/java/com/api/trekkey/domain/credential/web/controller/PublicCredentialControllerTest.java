package com.api.trekkey.domain.credential.web.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.trekkey.domain.credential.entity.CredentialType;
import com.api.trekkey.domain.credential.service.CredentialVerificationService;
import com.api.trekkey.domain.credential.service.dto.CredentialVerificationStatus;
import com.api.trekkey.domain.credential.service.dto.CredentialVerificationView;
import com.api.trekkey.global.exception.CustomException;
import com.api.trekkey.global.exception.GlobalExceptionHandler;
import com.api.trekkey.domain.credential.exception.CredentialErrorResponseCode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PublicCredentialControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CredentialVerificationService credentialVerificationService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new PublicCredentialController(credentialVerificationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void verify_returnsSuccessResponseEnvelope() throws Exception {
        given(credentialVerificationService.verify("cred-public-1"))
                .willReturn(new CredentialVerificationView(
                        CredentialVerificationStatus.VALID,
                        "cred-public-1",
                        "CERT-001",
                        CredentialType.AWARD,
                        "trekkey:award:v1:jcs-rfc8785:unicode-nfc-1",
                        "org-public-1",
                        "Trekkey University",
                        Instant.parse("2026-07-20T00:00:00Z"),
                        null,
                        List.of(),
                        new CredentialVerificationView.Evidence(
                                true,
                                true,
                                true,
                                true,
                                true,
                                true,
                                "0x" + "4".repeat(64),
                                "0x" + "5".repeat(64),
                                "0x" + "6".repeat(64),
                                "0x" + "7".repeat(64),
                                "0x" + "8".repeat(64),
                                "0x" + "9".repeat(64),
                                "batch-public-1",
                                "0x" + "a".repeat(64),
                                "0x" + "1".repeat(64),
                                1,
                                List.of(),
                                1001L,
                                "0x" + "2".repeat(40),
                                "0x" + "3".repeat(64),
                                12L),
                        null,
                        null));

        mockMvc.perform(get("/api/public/credentials/cred-public-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS_200"))
                .andExpect(jsonPath("$.httpStatus").value(200))
                .andExpect(jsonPath("$.data.verificationStatus").value("VALID"))
                .andExpect(jsonPath("$.data.credentialPublicId").value("cred-public-1"));
    }

    @Test
    void verify_returnsDomainErrorThroughGlobalHandler() throws Exception {
        given(credentialVerificationService.verify("missing"))
                .willThrow(new CustomException(CredentialErrorResponseCode.CREDENTIAL_NOT_FOUND));

        mockMvc.perform(get("/api/public/credentials/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("CREDENTIAL_404"));
    }
}
