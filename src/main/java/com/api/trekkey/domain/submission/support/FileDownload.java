package com.api.trekkey.domain.submission.support;

import java.io.InputStream;

// 파일 다운로드 응답 재료 — 컨트롤러가 스트리밍 응답으로 변환한다
public record FileDownload(
        String originalName,
        String contentType,
        long sizeBytes,
        InputStream inputStream
) {
}
