package com.api.trekkey.domain.submission.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.api.trekkey.global.exception.CustomException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageTest {

    @TempDir
    private Path tempDir;

    @Test
    void storeDeletesPartialObjectWhenInputFails() throws IOException {
        LocalFileStorage storage = new LocalFileStorage(tempDir.toString());

        assertThatThrownBy(() -> storage.store("submissions/test", "entry.pdf", failingInput()))
                .isInstanceOf(CustomException.class);

        try (var paths = Files.walk(tempDir)) {
            assertThat(paths.filter(Files::isRegularFile)).isEmpty();
        }
    }

    private InputStream failingInput() {
        return new InputStream() {
            private int readCount;

            @Override
            public int read() throws IOException {
                if (readCount++ == 0) {
                    return 1;
                }
                throw new IOException("simulated input failure");
            }
        };
    }
}
