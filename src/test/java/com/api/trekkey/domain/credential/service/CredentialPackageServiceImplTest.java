package com.api.trekkey.domain.credential.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.api.trekkey.domain.credential.entity.AncCredential;
import com.api.trekkey.domain.credential.entity.CredentialType;
import com.api.trekkey.domain.credential.exception.CredentialErrorResponseCode;
import com.api.trekkey.domain.credential.repository.AncBatchRepository;
import com.api.trekkey.domain.credential.repository.AncCredentialRepository;
import com.api.trekkey.domain.credential.service.dto.CredentialPackageFile;
import com.api.trekkey.domain.credential.service.dto.CredentialPackageFile;
import com.api.trekkey.domain.credential.service.dto.CredentialVerificationStatus;
import com.api.trekkey.domain.credential.service.dto.CredentialVerificationView;
import com.api.trekkey.global.exception.CustomException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CredentialPackageServiceImplTest {

    @Mock
    private CredentialVerificationService credentialVerificationService;

    @Mock
    private AncCredentialRepository credentialRepository;

    @Mock
    private AncBatchRepository batchRepository;

    @Mock
    private CredentialCertificateService credentialCertificateService;

    private CredentialPackageServiceImpl credentialPackageService;

    @BeforeEach
    void setUp() {
        credentialPackageService = new CredentialPackageServiceImpl(
                credentialVerificationService, credentialRepository, batchRepository,
                credentialCertificateService, new ObjectMapper());
        lenient().when(credentialCertificateService.renderCertificate("cred-pub-1"))
                .thenReturn(new CredentialPackageFile("cert.pdf", "%PDF-fake".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("패키지 zip에 §12 구성 파일이 전부 담기고 원문 바이트가 그대로 보존된다")
    void buildPackage_containsAllSpecFiles() throws Exception {
        AncCredential credential = mock(AncCredential.class);
        lenient().when(credential.getCanonicalBytes())
                .thenReturn("{\"credentialNo\":\"2026-C1-001\"}".getBytes(StandardCharsets.UTF_8));
        lenient().when(credential.getFileManifestCanonicalBytes())
                .thenReturn("[{\"sha256Hex\":\"0xabc\"}]".getBytes(StandardCharsets.UTF_8));
        given(credentialRepository.findByPublicId("cred-pub-1")).willReturn(Optional.of(credential));
        given(credentialVerificationService.verify("cred-pub-1")).willReturn(pendingView());

        CredentialPackageFile result = credentialPackageService.buildPackage("cred-pub-1");

        assertThat(result.fileName()).isEqualTo("trekkey-credential-2026-C1-001.zip");
        Map<String, byte[]> entries = unzip(result.zipBytes());
        assertThat(entries).containsKeys(
                "credential.json", "file-manifest.json", "merkle-proof.json",
                "anchor.json", "issuer-approval.json", "status.json",
                "rendered-certificate.pdf", "README.txt");
        assertThat(new String(entries.get("rendered-certificate.pdf"), StandardCharsets.UTF_8))
                .startsWith("%PDF-");
        //canonical 원문은 재직렬화 없이 바이트 그대로 (§7 — hash 재현성)
        assertThat(new String(entries.get("credential.json"), StandardCharsets.UTF_8))
                .isEqualTo("{\"credentialNo\":\"2026-C1-001\"}");

        JsonNode status = new ObjectMapper().readTree(entries.get("status.json"));
        assertThat(status.get("verificationStatus").asText()).isEqualTo("PENDING");
        JsonNode approval = new ObjectMapper().readTree(entries.get("issuer-approval.json"));
        assertThat(approval.get("state").asText()).isEqualTo("NOT_BATCHED"); //앵커링 전 상태 명시
    }

    @Test
    @DisplayName("존재하지 않는 Credential은 404")
    void buildPackage_throwsWhenNotFound() {
        given(credentialRepository.findByPublicId("missing")).willReturn(Optional.empty());

        assertThatThrownBy(() -> credentialPackageService.buildPackage("missing"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(CredentialErrorResponseCode.CREDENTIAL_NOT_FOUND);
    }

    //======= 헬퍼 메서드 ==========

    private CredentialVerificationView pendingView() {
        return new CredentialVerificationView(
                CredentialVerificationStatus.PENDING,
                "cred-pub-1",
                "2026-C1-001",
                CredentialType.AWARD,
                "trekkey:award:v1:jcs-rfc8785:unicode-nfc-1",
                "org-pub-1",
                "한성대학교",
                Instant.parse("2026-07-27T04:00:00Z"),
                null,
                List.of(),
                new CredentialVerificationView.Evidence(
                        true, true, true, true, true, true,
                        "0x" + "1".repeat(64), "0x" + "2".repeat(64), "0x" + "3".repeat(64),
                        "0x" + "4".repeat(64), "0x" + "5".repeat(64),
                        null, null, null, null, null, List.of(),
                        1001L, "", null, null),
                null,
                null);
    }

    private Map<String, byte[]> unzip(byte[] zipBytes) throws Exception {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        return entries;
    }
}
