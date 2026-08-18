package com.api.trekkey.domain.evidence.support;

import com.api.trekkey.domain.evidence.exception.EvidenceErrorResponseCode;
import com.api.trekkey.global.exception.CustomException;
import java.util.Arrays;
import org.springframework.stereotype.Component;

@Component
public class EvidenceFileInspector {
    public static final long MAX_SIZE = 10L * 1024 * 1024;
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    public String inspect(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new CustomException(EvidenceErrorResponseCode.EVIDENCE_FILE_REQUIRED);
        if (bytes.length > MAX_SIZE) throw new CustomException(EvidenceErrorResponseCode.EVIDENCE_FILE_TOO_LARGE);
        if (startsWith(bytes, PDF)) return "application/pdf";
        if (startsWith(bytes, PNG)) return "image/png";
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        throw new CustomException(EvidenceErrorResponseCode.EVIDENCE_FILE_TYPE_INVALID);
    }

    private boolean startsWith(byte[] actual, byte[] prefix) {
        return actual.length >= prefix.length && Arrays.equals(Arrays.copyOf(actual, prefix.length), prefix);
    }
}
