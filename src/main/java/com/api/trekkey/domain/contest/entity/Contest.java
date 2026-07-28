package com.api.trekkey.domain.contest.entity;

import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.user.entity.User;
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(
        uniqueConstraints = @UniqueConstraint(
                name = "uk_contest_public_id",
                columnNames = "public_id"))
public class Contest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // 대회 PK
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, length = 36)
    // 공개 URL과 API에서 사용할 불변 식별자
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    // 대회를 운영하는 학교/기관
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id", nullable = false)
    // 대회를 담당하는 관리자 사용자
    private User ownerUser;

    @Column(nullable = false, length = 150)
    // 대회명
    private String title;

    @Column(nullable = false, length = 100)
    // 주관 부서
    private String department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    // 대회 전체 진행 상태
    private ContestStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "participation_type", nullable = false, length = 30)
    // 참가 방식: 팀전, 개인전, 개인/팀
    private ParticipationType participationType;

    @Builder.Default
    @Column(nullable = false)
    // 예정 시상 수
    private int awardCount = 0;

    @Column(length = 500)
    // 공개 페이지에 노출할 대표 포스터 URL
    private String posterUrl;

    @Column(nullable = false, length = 300)
    // 공개 페이지 한 줄 소개
    private String summary;

    @Column(nullable = false, length = 300)
    // 참가 대상 안내
    private String target;

    @Column(nullable = false, length = 500)
    // 참가 신청 방법 안내
    private String applicationMethod;

    @Column(nullable = false, length = 500)
    // 시상 및 혜택 안내
    private String benefits;

    @Column(length = 500)
    // 검색과 공개 노출에 사용할 쉼표 구분 태그
    private String tags;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    // 공개 공고 상세 HTML 본문
    private String detailHtml;

    @Builder.Default
    @Column(nullable = false)
    // 공개 상세 페이지 조회 수
    private long viewCount = 0L;

    @PrePersist
    private void assignPublicId() {
        if (publicId == null || publicId.isBlank()) {
            publicId = UUID.randomUUID().toString();
        }
    }

    // 수상 확정 등 운영 흐름에서 대회 상태만 전환한다.
    public void changeStatus(ContestStatus status) {
        this.status = status;
    }

    // 관리자 대회 편집 — 공개 공고 정보 전체를 갱신한다. (setter 대신 도메인 메서드)
    public void update(
            String title,
            String department,
            ContestStatus status,
            ParticipationType participationType,
            int awardCount,
            String posterUrl,
            String summary,
            String target,
            String applicationMethod,
            String benefits,
            String tags,
            String detailHtml
    ) {
        this.title = title;
        this.department = department;
        this.status = status;
        this.participationType = participationType;
        this.awardCount = awardCount;
        this.posterUrl = posterUrl;
        this.summary = summary;
        this.target = target;
        this.applicationMethod = applicationMethod;
        this.benefits = benefits;
        this.tags = tags;
        this.detailHtml = detailHtml;
    }
}
