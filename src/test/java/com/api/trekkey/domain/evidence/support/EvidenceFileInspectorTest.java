package com.api.trekkey.domain.evidence.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.api.trekkey.global.exception.CustomException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class EvidenceFileInspectorTest {
    private final EvidenceFileInspector inspector = new EvidenceFileInspector();

    @Test
    void parsesSupportedDocumentsInsteadOfTrustingMagicBytesOnly() throws Exception {
        assertThat(inspector.inspect(pdf())).isEqualTo("application/pdf");
        assertThat(inspector.inspect(image("jpg"))).isEqualTo("image/jpeg");
        assertThat(inspector.inspect(image("png"))).isEqualTo("image/png");
    }

    @Test
    void rejectsTruncatedFilesThatOnlyHaveAValidHeader() {
        assertInvalid("%PDF-1.7 body".getBytes());
        assertInvalid(new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00});
        assertInvalid(new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
    }

    @Test
    void parsesAnActualExternalDocumentWhenSmokePathIsProvided() throws Exception {
        String path = System.getenv("EVIDENCE_SMOKE_FILE");
        Assumptions.assumeTrue(path != null && !path.isBlank(), "EVIDENCE_SMOKE_FILE not set");
        assertThat(inspector.inspect(Files.readAllBytes(Path.of(path)))).isEqualTo("application/pdf");
    }

    @Test
    void rejectsExecutableRenamedAsPdf() {
        assertThatThrownBy(() -> inspector.inspect("MZ executable".getBytes()))
                .isInstanceOf(CustomException.class)
                .satisfies(error -> assertThat(((CustomException) error).getBaseResponseCode().getCode())
                        .isEqualTo("EVIDENCE_FILE_TYPE_INVALID"));
    }

    @Test
    void rejectsOversizedFileBeforeStorage() {
        assertThatThrownBy(() -> inspector.inspect(new byte[(int) EvidenceFileInspector.MAX_SIZE + 1]))
                .isInstanceOf(CustomException.class)
                .satisfies(error -> assertThat(((CustomException) error).getBaseResponseCode().getCode())
                        .isEqualTo("EVIDENCE_FILE_TOO_LARGE"));
    }

    private void assertInvalid(byte[] bytes) {
        assertThatThrownBy(() -> inspector.inspect(bytes))
                .isInstanceOf(CustomException.class)
                .satisfies(error -> assertThat(((CustomException) error).getBaseResponseCode().getCode())
                        .isEqualTo("EVIDENCE_FILE_TYPE_INVALID"));
    }

    private byte[] pdf() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document();
        PdfWriter.getInstance(document, output);
        document.open();
        document.add(new Paragraph("non-PII graduation evidence fixture"));
        document.close();
        return output.toByteArray();
    }

    private byte[] image(String format) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }
}
