package com.api.trekkey.domain.credential.entity;

import com.api.trekkey.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
        name = "anc_credential_subject",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_anc_credential_subject_order",
                        columnNames = {"credential_id", "subject_order"}),
                @UniqueConstraint(
                        name = "uk_anc_credential_subject_identity",
                        columnNames = {"credential_id", "subject_ref", "role_code"})
        },
        indexes = {
                @Index(name = "idx_anc_credential_subject_user", columnList = "user_id"),
                @Index(name = "idx_anc_credential_subject_team", columnList = "team_id")
        })
public class AncCredentialSubject extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "credential_id", nullable = false, updatable = false)
    private Long credentialId;

    @Column(name = "user_id", updatable = false)
    private Long userId;

    @Column(name = "team_id", updatable = false)
    private Long teamId;

    @Column(name = "subject_ref", nullable = false, length = 128, updatable = false)
    private String subjectRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, length = 20, updatable = false)
    private CredentialSubjectType subjectType;

    @Column(name = "display_name_snapshot", nullable = false, length = 255, updatable = false)
    private String displayNameSnapshot;

    @Column(name = "major_snapshot", length = 255, updatable = false)
    private String majorSnapshot;

    @Column(name = "role_code", nullable = false, length = 50, updatable = false)
    private String roleCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "disclosure_class", nullable = false, length = 20, updatable = false)
    private DisclosureClass disclosureClass;

    @Column(name = "subject_order", nullable = false, updatable = false)
    private int subjectOrder;

    private AncCredentialSubject(
            Long credentialId,
            Long userId,
            Long teamId,
            String subjectRef,
            CredentialSubjectType subjectType,
            String displayNameSnapshot,
            String majorSnapshot,
            String roleCode,
            DisclosureClass disclosureClass,
            int subjectOrder) {
        if (credentialId == null || credentialId <= 0 || isBlank(subjectRef) || subjectType == null
                || isBlank(displayNameSnapshot) || (majorSnapshot != null && majorSnapshot.isBlank())
                || isBlank(roleCode) || disclosureClass == null || subjectOrder < 0) {
            throw new IllegalArgumentException("credential subject immutable fields are required");
        }
        if ((userId != null && userId <= 0) || (teamId != null && teamId <= 0)) {
            throw new IllegalArgumentException("subject reference ids must be positive");
        }
        boolean validReference = (subjectType == CredentialSubjectType.USER && userId != null && teamId == null)
                || (subjectType == CredentialSubjectType.TEAM && teamId != null && userId == null);
        if (!validReference) {
            throw new IllegalArgumentException("credential subject must have exactly one matching subject reference");
        }
        this.credentialId = credentialId;
        this.userId = userId;
        this.teamId = teamId;
        this.subjectRef = subjectRef;
        this.subjectType = subjectType;
        this.displayNameSnapshot = displayNameSnapshot;
        this.majorSnapshot = majorSnapshot;
        this.roleCode = roleCode;
        this.disclosureClass = disclosureClass;
        this.subjectOrder = subjectOrder;
    }

    public static AncCredentialSubject of(
            Long credentialId,
            Long userId,
            Long teamId,
            String subjectRef,
            CredentialSubjectType subjectType,
            String displayNameSnapshot,
            String majorSnapshot,
            String roleCode,
            DisclosureClass disclosureClass,
            int subjectOrder) {
        return new AncCredentialSubject(
                credentialId, userId, teamId, subjectRef, subjectType, displayNameSnapshot, majorSnapshot,
                roleCode, disclosureClass, subjectOrder);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
