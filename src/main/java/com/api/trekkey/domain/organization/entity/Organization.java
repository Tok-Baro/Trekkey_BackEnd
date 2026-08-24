package com.api.trekkey.domain.organization.entity;

import com.api.trekkey.global.entity.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Organization extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", unique = true, length = 36)
    private String publicId;

    @Column(name = "code", nullable = false, unique = true, length = 50, updatable = false)
    private String code;

    private String name; //학교이름

    @Enumerated(EnumType.STRING)
    private OrganizationStatus status;

    @PrePersist
    void assignPublicId() {
        ensurePublicId();
        ensureCode();
    }

    public String ensurePublicId() {
        if (publicId == null) {
            publicId = UUID.randomUUID().toString();
        }
        return publicId;
    }

    public String ensureCode() {
        if (code == null || code.isBlank()) {
            code = "ORG_" + UUID.randomUUID().toString().replace("-", "");
        }
        return code;
    }
}
