package com.api.trekkey.domain.credential.support;

import com.api.trekkey.domain.credential.entity.CredentialType;
import com.api.trekkey.domain.credential.service.dto.CredentialVerificationView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 상장·확인서 PDF 렌더링 (erd-mvp §12 rendered-certificate.pdf).
 * PDF는 사람이 읽는 표시물이며 cryptographic source of truth가 아니다 —
 * 진위는 QR의 공개 검증 URL(§9: 개인정보 없이 publicId만)로 확인한다.
 */
@Component
public class CredentialCertificateRenderer {

    private final BaseFont regular;
    private final BaseFont bold;

    public CredentialCertificateRenderer() {
        this.regular = loadFont("fonts/NanumGothic-Regular.ttf");
        this.bold = loadFont("fonts/NanumGothic-Bold.ttf");
    }

    public byte[] render(CredentialVerificationView view, String contestTitle,
                         String prize, String submissionTitle, String verifyUrl) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4, 70, 70, 90, 70);
            PdfWriter writer = PdfWriter.getInstance(document, buffer);
            document.open();
            drawBorder(writer);

            //우상단 증서 번호
            Paragraph number = new Paragraph("제 " + view.credentialNo() + " 호", font(regular, 11));
            number.setAlignment(Element.ALIGN_RIGHT);
            document.add(number);

            //제목 — 유형별 (상장 / 작품 확인서 / 참여 확인서)
            Paragraph title = new Paragraph(titleOf(view.credentialType()), font(bold, 40));
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingBefore(30);
            title.setSpacingAfter(view.credentialType() == CredentialType.AWARD ? 6 : 28);
            document.add(title);

            //상격 (수상 전용)
            if (view.credentialType() == CredentialType.AWARD && prize != null) {
                Paragraph prizeLine = new Paragraph(prize, font(bold, 22));
                prizeLine.setAlignment(Element.ALIGN_CENTER);
                prizeLine.setSpacingAfter(24);
                document.add(prizeLine);
            }

            //수여 대상 — 팀 + 발급 당시 구성원 snapshot
            for (String line : subjectLines(view.publicSubjects())) {
                Paragraph subject = new Paragraph(line, font(bold, 16));
                subject.setAlignment(Element.ALIGN_CENTER);
                document.add(subject);
            }

            //본문
            Paragraph bodyText = new Paragraph(bodyOf(view.credentialType(), contestTitle, submissionTitle),
                    font(regular, 14));
            bodyText.setAlignment(Element.ALIGN_CENTER);
            bodyText.setLeading(26);
            bodyText.setSpacingBefore(34);
            document.add(bodyText);

            //발급일·발급기관
            LocalDate issued = LocalDate.ofInstant(view.issuedAt(), ZoneOffset.UTC);
            Paragraph date = new Paragraph(
                    "%d년 %d월 %d일".formatted(issued.getYear(), issued.getMonthValue(), issued.getDayOfMonth()),
                    font(regular, 14));
            date.setAlignment(Element.ALIGN_CENTER);
            date.setSpacingBefore(42);
            document.add(date);

            Paragraph issuer = new Paragraph(view.issuerName(), font(bold, 24));
            issuer.setAlignment(Element.ALIGN_CENTER);
            issuer.setSpacingBefore(14);
            document.add(issuer);

            //하단 QR + 검증 안내 (§9 — URL과 publicId만, 개인정보 미포함)
            Image qr = Image.getInstance(qrPng(verifyUrl));
            qr.scaleAbsolute(84, 84);
            qr.setAbsolutePosition(70, 72);
            document.add(qr);
            PdfContentByte canvas = writer.getDirectContent();
            canvas.beginText();
            canvas.setFontAndSize(regular, 9);
            canvas.showTextAligned(Element.ALIGN_LEFT,
                    "본 증서의 진위는 블록체인 앵커링 기반 공개 검증으로 확인할 수 있습니다.", 165, 122, 0);
            canvas.showTextAligned(Element.ALIGN_LEFT, verifyUrl, 165, 106, 0);
            canvas.showTextAligned(Element.ALIGN_LEFT,
                    "증서 번호 " + view.credentialNo(), 165, 90, 0);
            canvas.endText();

            document.close();
            return buffer.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("증서 PDF 렌더링에 실패했습니다", exception);
        }
    }

    //======= 헬퍼 메서드 ==========

    private String titleOf(CredentialType type) {
        return switch (type) {
            case AWARD -> "상  장";
            case WORK -> "작 품 확 인 서";
            case PARTICIPATION -> "참 여 확 인 서";
        };
    }

    private List<String> subjectLines(List<CredentialVerificationView.PublicSubject> subjects) {
        String teamName = subjects.stream()
                .filter(subject -> "TEAM".equals(subject.subjectType()))
                .map(CredentialVerificationView.PublicSubject::displayName)
                .findFirst().orElse("");
        String members = subjects.stream()
                .filter(subject -> "USER".equals(subject.subjectType()))
                .map(subject -> subject.displayName()
                        + ("REPRESENTATIVE".equals(subject.roleCode()) ? " (대표)" : ""))
                .reduce((a, b) -> a + " · " + b).orElse("");
        return List.of("팀  " + teamName, members);
    }

    private String bodyOf(CredentialType type, String contestTitle, String submissionTitle) {
        String contest = contestTitle == null ? "본 대회" : "「" + contestTitle + "」";
        return switch (type) {
            case AWARD -> "위 팀은 " + contest + "에서\n위와 같이 수상하였기에 이 상장을 수여합니다.";
            case WORK -> "위 팀은 " + contest + "에\n작품 「" + submissionTitle + "」(을)를 출품하여\n심사에 회부되었음을 확인합니다.";
            case PARTICIPATION -> "위 팀은 " + contest + "에\n참가하였음을 확인합니다.";
        };
    }

    private void drawBorder(PdfWriter writer) {
        PdfContentByte canvas = writer.getDirectContent();
        canvas.setLineWidth(2.2f);
        canvas.rectangle(36, 36, PageSize.A4.getWidth() - 72, PageSize.A4.getHeight() - 72);
        canvas.stroke();
        canvas.setLineWidth(0.8f);
        canvas.rectangle(44, 44, PageSize.A4.getWidth() - 88, PageSize.A4.getHeight() - 88);
        canvas.stroke();
    }

    private byte[] qrPng(String url) throws Exception {
        BitMatrix matrix = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 240, 240);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", png);
        return png.toByteArray();
    }

    private Font font(BaseFont base, float size) {
        return new Font(base, size);
    }

    private BaseFont loadFont(String classpath) {
        try {
            byte[] bytes = new ClassPathResource(classpath).getInputStream().readAllBytes();
            return BaseFont.createFont(classpath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, bytes, null);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
