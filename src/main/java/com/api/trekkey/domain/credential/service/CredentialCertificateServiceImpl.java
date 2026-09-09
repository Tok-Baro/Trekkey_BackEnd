package com.api.trekkey.domain.credential.service;

import com.api.trekkey.domain.credential.service.dto.CredentialPackageFile;
import com.api.trekkey.domain.credential.service.dto.CredentialVerificationView;
import com.api.trekkey.domain.credential.support.CredentialCertificateRenderer;
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
    private final CredentialCertificateRenderer certificateRenderer;

    @Value("${app.front.base-url}")
    private String frontBaseUrl;

    @Override
    public CredentialPackageFile renderCertificate(String credentialPublicId) {
        CredentialVerificationView view = credentialVerificationService.verify(credentialPublicId);

        CredentialVerificationView.PublicDetails details = view.publicDetails();
        byte[] pdf = certificateRenderer.render(
                view,
                details == null ? null : details.contestTitle(),
                details == null ? null : details.prize(),
                details == null ? null : details.submissionTitle(),
                frontBaseUrl + "/verify/" + credentialPublicId);

        return new CredentialPackageFile(
                "trekkey-certificate-" + view.credentialNo() + ".pdf", pdf);
    }

}
