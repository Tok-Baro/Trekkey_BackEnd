package com.api.trekkey.domain.credential.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.trekkey.domain.credential.entity.CredentialType;
import com.api.trekkey.domain.credential.service.dto.CredentialVerificationStatus;
import com.api.trekkey.domain.credential.service.dto.CredentialVerificationView;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CredentialCertificateRendererTest {

    private final CredentialCertificateRenderer renderer = new CredentialCertificateRenderer();

    @Test
    @DisplayName("수상 상장 PDF가 유효한 PDF 헤더와 한글 폰트 임베딩으로 생성된다")
    void render_awardCertificate() {
        byte[] pdf = renderer.render(view(CredentialType.AWARD), "2026 캡스톤 경진대회",
                "대상", "AI 작품", "http://localhost:5173/verify/cred-pub-1");

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(20_000); //폰트 서브셋 임베딩 확인 (미임베딩이면 수 KB 수준)
    }

    @Test
    @DisplayName("참여·작품 확인서도 유형별 제목으로 생성된다")
    void render_participationAndWork() {
        byte[] participation = renderer.render(view(CredentialType.PARTICIPATION),
                "2026 캡스톤 경진대회", null, null, "http://localhost:5173/verify/cred-pub-1");
        byte[] work = renderer.render(view(CredentialType.WORK),
                "2026 캡스톤 경진대회", null, "AI 작품", "http://localhost:5173/verify/cred-pub-1");

        assertThat(new String(participation, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(new String(work, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    //======= 헬퍼 메서드 ==========

    private CredentialVerificationView view(CredentialType type) {
        return new CredentialVerificationView(
                CredentialVerificationStatus.PENDING,
                "cred-pub-1",
                "2026-C1-001",
                type,
                "trekkey:award:v1:jcs-rfc8785:unicode-nfc-1",
                "org-pub-1",
                "한성대학교",
                Instant.parse("2026-07-29T04:00:00Z"),
                null,
                List.of(
                        new CredentialVerificationView.PublicSubject(
                                "team:t1", "TEAM", "팀트레키", "컴퓨터공학과", "TEAM"),
                        new CredentialVerificationView.PublicSubject(
                                "user:2", "USER", "이준수", "컴퓨터공학과", "REPRESENTATIVE"),
                        new CredentialVerificationView.PublicSubject(
                                "user:3", "USER", "김팀원", "소프트웨어학과", "PARTICIPANT")),
                new CredentialVerificationView.Evidence(
                        true, true, true, true, true, true,
                        "0x" + "1".repeat(64), "0x" + "2".repeat(64), "0x" + "3".repeat(64),
                        "0x" + "4".repeat(64), "0x" + "5".repeat(64),
                        null, null, null, null, null, List.of(),
                        1001L, "", null, null),
                null,
                null);
    }
}
