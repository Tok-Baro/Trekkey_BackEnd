package com.api.trekkey.domain.credential.service;

import com.api.trekkey.domain.credential.entity.AncCredential;
import com.api.trekkey.domain.credential.exception.CredentialErrorResponseCode;
import com.api.trekkey.domain.credential.repository.AncCredentialRepository;
import com.api.trekkey.domain.credential.service.dto.CredentialPackageFile;
import com.api.trekkey.domain.credential.service.dto.CredentialVerificationView;
import com.api.trekkey.domain.credential.support.CredentialCertificateRenderer;
import com.api.trekkey.global.exception.CustomException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상장·확인서 PDF 발급 (erd-mvp §12).
 * 표시 내용은 업무 테이블이 아니라 발급 시점 불변 payload에서 읽는다 —
 * 이후 대회명·상격이 바뀌어도 증서는 발급 당시 그대로다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CredentialCertificateServiceImpl implements CredentialCertificateService {

    private final CredentialVerificationService credentialVerificationService;
    private final AncCredentialRepository credentialRepository;
    private final CredentialCertificateRenderer certificateRenderer;
    private final ObjectMapper objectMapper;

    @Value("${app.front.base-url}")
    private String frontBaseUrl;

    @Override
    public CredentialPackageFile renderCertificate(String credentialPublicId) {
        AncCredential credential = credentialRepository.findByPublicId(credentialPublicId)
                .orElseThrow(() -> new CustomException(CredentialErrorResponseCode.CREDENTIAL_NOT_FOUND));
        CredentialVerificationView view = credentialVerificationService.verify(credentialPublicId);

        JsonNode snapshot = readSnapshot(credential.getPayloadJson());
        byte[] pdf = certificateRenderer.render(
                view,
                snapshot.path("contestTitle").asText(null),
                snapshot.path("prize").asText(null),
                snapshot.path("submissionTitle").asText(null),
                frontBaseUrl + "/verify/" + credentialPublicId);

        return new CredentialPackageFile(
                "trekkey-certificate-" + view.credentialNo() + ".pdf", pdf);
    }

    //======= 헬퍼 메서드 ==========

    private JsonNode readSnapshot(String payloadJson) {
        try {
            return objectMapper.readTree(payloadJson).path("source").path("snapshot");
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
    }
}
