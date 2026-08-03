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

    // 재제출은 현재 제출물의 제목과 파일을 교체한다. 별도 버전 이력은 만들지 않는다.
    public void overwrite(String title, LocalDateTime now) {
        this.title = title;
        this.status = SubmissionStatus.SUBMITTED;
        this.submittedAt = now;
    }

    // 제출을 확정한다. 심사가 시작되면 호출되며 이후 덮어쓰기를 거부한다.
    public boolean finalizeSubmission(LocalDateTime now) {
        return finalizeAt(now);
    }

    public boolean isFinalized() {
        return finalizedAt != null;
    }

    /**
     * 제출물을 심사 대상으로 확정한다.
     *
     * <p>제출 완료 상태만 한 번 확정할 수 있도록 엔티티 수준에서도 보호한다.
     * 같은 제출물을 다시 확정하려는 호출은 최초 확정 시각을 유지한다.</p>
     */
    public boolean finalizeAt(LocalDateTime now) {
        if (now == null
                || isFinalized()
                || status != SubmissionStatus.SUBMITTED) {
            return false;
        }
        this.finalizedAt = now;
        return true;
    }

}
