package com.api.trekkey.domain.submission.entity;

import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import java.time.LocalDateTime;
import java.util.UUID;
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
public class Submission extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // 최종 제출물 PK
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, unique = true, length = 36)
    // 공개 URL과 API에서 사용할 불변 식별자
    private String publicId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false, unique = true)
    // 제출 팀 — 팀당 최종 제출물 한 건 (erd-mvp §2, 마감 전 덮어쓰기)
    private Team team;

    @Column(nullable = false, length = 150)
    // 작품명
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    // 제출 상태
    private SubmissionStatus status;

    @Builder.Default
    @Column(nullable = false)
    // 재제출마다 증가 — Credential source 버전 (erd-mvp §5)
    private long sourceVersion = 1L;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    // 파일 해시 확정 여부
    private IntegrityStatus integrityStatus;

    // 제출 수정 마감 시각 — 심사 시작 등으로 잠기면 기록 (null이면 수정 가능)
    private LocalDateTime finalizedAt;

    @Column(nullable = false)
    // 최근 제출 시각
    private LocalDateTime submittedAt;

    @PrePersist
    private void assignPublicId() {
        if (publicId == null || publicId.isBlank()) {
            publicId = UUID.randomUUID().toString();
        }
    }

    // 재제출(덮어쓰기) — 제목을 교체하고 source 버전을 올린다. 파일 교체는 서비스가 수행한다. (erd-mvp §5)
    public void overwrite(String title, LocalDateTime now) {
        this.title = title;
        this.sourceVersion += 1;
        this.integrityStatus = IntegrityStatus.STALE;
        this.status = SubmissionStatus.SUBMITTED;
        this.submittedAt = now;
    }

    // 전체 파일 해시 확정 — Credential 발급 가능 상태로 전환
    public void markIntegrityReady() {
        this.integrityStatus = IntegrityStatus.READY;
    }

    // 제출 잠금 — 이후 덮어쓰기를 거부한다
    public void finalizeSubmission(LocalDateTime now) {
        this.finalizedAt = now;
    }

    public boolean isFinalized() {
        return finalizedAt != null;
    }
}
