package com.api.trekkey.domain.evidence.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.api.trekkey.global.exception.CustomException;
import org.junit.jupiter.api.Test;

class EvidenceFileInspectorTest {
    private final EvidenceFileInspector inspector = new EvidenceFileInspector();

    @Test
    void detectsSupportedFileByMagicBytesNotClaimedMime() {
        assertThat(inspector.inspect("%PDF-1.7 body".getBytes())).isEqualTo("application/pdf");
        assertThat(inspector.inspect(new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00}))
                .isEqualTo("image/jpeg");
        assertThat(inspector.inspect(new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10}))
                .isEqualTo("image/png");
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
}
