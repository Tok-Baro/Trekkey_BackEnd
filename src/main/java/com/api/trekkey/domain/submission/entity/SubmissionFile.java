package com.api.trekkey.domain.submission.entity;

import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SubmissionFile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // 제출 파일 PK
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    // 소속 제출물
    private Submission submission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_user_id", nullable = false)
    // 업로드한 사용자
    private User uploadedBy;

    @Column(nullable = false, length = 255)
    // 원본 파일명
    private String originalName;

    @Column(nullable = false, length = 100)
    // MIME 타입
    private String contentType;

    @Column(nullable = false)
    // 파일 크기 (bytes)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false, unique = true, length = 500)
    // 저장소 키 — 다운로드 URL은 저장하지 않는다 (erd-mvp §7)
    private String storageKey;

    @Column(nullable = false, length = 64)
    // 서버가 저장 스트림에서 계산한 SHA-256 (hex)
    private String sha256;
}
