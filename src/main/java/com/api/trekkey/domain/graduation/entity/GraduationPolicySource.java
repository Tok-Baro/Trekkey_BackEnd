package com.api.trekkey.domain.graduation.entity;

import com.api.trekkey.domain.graduation.entity.GraduationTypes.PolicySourceType;
import com.api.trekkey.global.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "graduation_policy_source", uniqueConstraints = @UniqueConstraint(
        name = "uk_policy_source_hash_locator", columnNames = {"policy_id", "content_hash", "source_locator"}))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GraduationPolicySource extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private GraduationPolicy policy;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private PolicySourceType sourceType;

    @Column(name = "official_url", nullable = false, length = 1000)
    private String officialUrl;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "published_at")
    private LocalDate publishedAt;

    @Column(name = "retrieved_at", nullable = false)
    private LocalDateTime retrievedAt;

    @Column(name = "content_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String contentHash;

    @Column(name = "source_locator", length = 300)
    private String sourceLocator;

    @Column(name = "stored_object_key", length = 500)
    private String storedObjectKey;
}
