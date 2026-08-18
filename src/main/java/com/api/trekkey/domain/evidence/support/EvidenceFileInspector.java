package com.api.trekkey.domain.evidence.support;

import com.lowagie.text.pdf.PdfReader;
import com.api.trekkey.domain.evidence.exception.EvidenceErrorResponseCode;
import com.api.trekkey.global.exception.CustomException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Component;

@Component
public class EvidenceFileInspector {
    public static final long MAX_SIZE = 10L * 1024 * 1024;
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    public String inspect(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new CustomException(EvidenceErrorResponseCode.EVIDENCE_FILE_REQUIRED);
        if (bytes.length > MAX_SIZE) throw new CustomException(EvidenceErrorResponseCode.EVIDENCE_FILE_TOO_LARGE);
        if (startsWith(bytes, PDF)) return validPdf(bytes);
        if (startsWith(bytes, PNG)) return validImage(bytes, "image/png");
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
            return validImage(bytes, "image/jpeg");
        }
        throw new CustomException(EvidenceErrorResponseCode.EVIDENCE_FILE_TYPE_INVALID);
    }

    private String validPdf(byte[] bytes) {
        try {
            PdfReader reader = new PdfReader(bytes);
            try {
                if (reader.getNumberOfPages() < 1) invalid();
            } finally {
                reader.close();
            }
            return "application/pdf";
        } catch (Exception exception) {
            return invalid();
        }
    }

    private String validImage(byte[] bytes, String contentType) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = input == null
                    ? java.util.Collections.emptyIterator() : ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return invalid();
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > 40_000_000L) return invalid();
                if (reader.read(0) == null) return invalid();
            } finally {
                reader.dispose();
            }
            return contentType;
        } catch (IOException exception) {
            return invalid();
        }
    }

    private String invalid() {
        throw new CustomException(EvidenceErrorResponseCode.EVIDENCE_FILE_TYPE_INVALID);
    }

    private boolean startsWith(byte[] actual, byte[] prefix) {
        return actual.length >= prefix.length && Arrays.equals(Arrays.copyOf(actual, prefix.length), prefix);
    }
}
