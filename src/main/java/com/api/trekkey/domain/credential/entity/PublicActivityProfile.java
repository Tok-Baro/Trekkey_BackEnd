package com.api.trekkey.domain.credential.entity;

import com.api.trekkey.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "public_activity_profile",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_public_activity_profile_user", columnNames = "user_id"),
                @UniqueConstraint(name = "uk_public_activity_profile_public_id", columnNames = "public_id")
        },
        indexes = @Index(name = "idx_public_activity_profile_lookup", columnList = "public_id,enabled"))
public class PublicActivityProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "public_id", nullable = false, length = 36)
    private String publicId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    private PublicActivityProfile(Long userId, String publicId) {
        if (userId == null || userId <= 0 || publicId == null || publicId.isBlank()) {
            throw new IllegalArgumentException("public activity profile fields are required");
        }
        this.userId = userId;
        this.publicId = publicId;
        this.enabled = true;
    }

    public static PublicActivityProfile enabled(Long userId, String publicId) {
        return new PublicActivityProfile(userId, publicId);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void rotatePublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            throw new IllegalArgumentException("public activity profile ID is required");
        }
        this.publicId = publicId;
    }
}
