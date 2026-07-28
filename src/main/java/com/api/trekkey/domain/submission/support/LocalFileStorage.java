package com.api.trekkey.domain.submission.support;

import com.api.trekkey.domain.submission.exception.SubmissionErrorResponseCode;
import com.api.trekkey.global.exception.CustomException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LocalFileStorage implements FileStoragePort {

    private final Path rootDir;

    public LocalFileStorage(@Value("${app.file.upload-dir}") String uploadDir) {
        this.rootDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public StoredFile store(String keyPrefix, String originalName, InputStream inputStream) {
        // 원본 파일명은 DB에만 남기고, 저장 키는 UUID + 확장자로 생성한다 (경로 조작 방지)
        String extension = extractExtension(originalName);
        String storageKey = keyPrefix + "/" + UUID.randomUUID() + extension;
        Path target = resolveSafely(storageKey);

        try {
            Files.createDirectories(target.getParent());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size;
            try (DigestInputStream digestStream = new DigestInputStream(inputStream, digest);
                 OutputStream out = Files.newOutputStream(target)) {
                size = digestStream.transferTo(out);
            }
            return new StoredFile(storageKey, size, HexFormat.of().formatHex(digest.digest()));
        } catch (IOException e) {
            deletePartialFile(target, storageKey);
            log.error("파일 저장 실패: {}", storageKey, e);
            throw new CustomException(SubmissionErrorResponseCode.SUBMISSION_STORAGE_ERROR);
        } catch (RuntimeException e) {
            deletePartialFile(target, storageKey);
            throw e;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available.", e);
        }
    }

    @Override
    public InputStream open(String storageKey) {
        try {
            return Files.newInputStream(resolveSafely(storageKey));
        } catch (IOException e) {
            log.error("파일 열기 실패: {}", storageKey, e);
            throw new CustomException(SubmissionErrorResponseCode.SUBMISSION_FILE_NOT_FOUND);
        }
    }

    @Override
    public void delete(String storageKey) {
        // 이전 객체 정리 실패는 본 트랜잭션을 깨지 않는다 (erd-mvp §5 — 비동기 정리 성격)
        try {
            Files.deleteIfExists(resolveSafely(storageKey));
        } catch (IOException e) {
            log.warn("파일 삭제 실패 (수동 정리 필요): {}", storageKey, e);
        }
    }

    private void deletePartialFile(Path target, String storageKey) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException cleanupException) {
            log.warn("부분 저장 파일 정리 실패 (수동 정리 필요): {}", storageKey, cleanupException);
        }
    }

    private Path resolveSafely(String storageKey) {
        Path resolved = rootDir.resolve(storageKey).normalize();
        if (!resolved.startsWith(rootDir)) {
            throw new CustomException(SubmissionErrorResponseCode.SUBMISSION_FILE_NOT_FOUND);
        }
        return resolved;
    }

    private String extractExtension(String originalName) {
        int dotIndex = originalName == null ? -1 : originalName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == originalName.length() - 1) {
            return "";
        }
        return originalName.substring(dotIndex).toLowerCase();
    }
}
