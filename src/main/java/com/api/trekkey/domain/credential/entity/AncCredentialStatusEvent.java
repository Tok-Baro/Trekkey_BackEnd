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
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "anc_credential_status_event",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_anc_credential_status_event_credential", columnNames = "credential_id"),
                @UniqueConstraint(name = "uk_anc_credential_status_event_digest", columnNames = "approval_digest"),
                @UniqueConstraint(name = "uk_anc_credential_status_event_idempotency", columnNames = "idempotency_key")
        },
        indexes = {
                @Index(name = "idx_anc_credential_status_event_credential", columnList = "credential_id"),
                @Index(name = "idx_anc_credential_status_event_issuer_key", columnList = "issuer_key_id")
        })
public class AncCredentialStatusEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "credential_id", nullable = false, updatable = false)
    private Long credentialId;

    @Column(name = "issuer_key_id", nullable = false, updatable = false)
    private Long issuerKeyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 20, updatable = false)
    private CredentialStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "next_status", nullable = false, length = 20, updatable = false)
    private CredentialStatus nextStatus;

    @Column(name = "reason_code", nullable = false, length = 100, updatable = false)
    private String reasonCode;

    @Lob
    @Column(columnDefinition = "MEDIUMTEXT", name = "reason_detail", updatable = false)
    private String reasonDetail;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private Long actorUserId;

    @Column(name = "superseding_credential_id", updatable = false)
    private Long supersedingCredentialId;

    @Column(name = "approval_nonce", nullable = false)
    private long approvalNonce;

    @Column(name = "approval_deadline", nullable = false)
    private LocalDateTime approvalDeadline;

    @Lob
    @Column(columnDefinition = "MEDIUMTEXT", name = "approval_payload_json")
    private String approvalPayloadJson;

    @Getter(AccessLevel.NONE)
    @Column(name = "approval_digest", length = 32, unique = true)
    private byte[] approvalDigest;

    @Getter(AccessLevel.NONE)
    @Column(name = "issuer_signature", length = 65)
    private byte[] issuerSignature;

    @Column(name = "idempotency_key", nullable = false, length = 128, updatable = false)
    private String idempotencyKey;

    @Column(name = "effective_at", nullable = false)
    private LocalDateTime effectiveAt;

    private AncCredentialStatusEvent(
            Long credentialId,
            Long issuerKeyId,
            CredentialStatus previousStatus,
            CredentialStatus nextStatus,
            String reasonCode,
            String reasonDetail,
            Long actorUserId,
            Long supersedingCredentialId,
            long approvalNonce,
            LocalDateTime approvalDeadline,
            String idempotencyKey,
            LocalDateTime effectiveAt) {
        if (credentialId == null || credentialId <= 0 || issuerKeyId == null || issuerKeyId <= 0
                || previousStatus == null || reasonCode == null || reasonCode.isBlank()
                || actorUserId == null || actorUserId <= 0 || approvalNonce < 0 || approvalDeadline == null
                || idempotencyKey == null || idempotencyKey.isBlank()
                || effectiveAt == null) {
            throw new IllegalArgumentException("credential status event required fields are missing");
        }
        if (previousStatus != CredentialStatus.ANCHORED) {
            throw new IllegalArgumentException("status event must start from ANCHORED");
        }
        if (nextStatus != CredentialStatus.REVOKED && nextStatus != CredentialStatus.SUPERSEDED) {
            throw new IllegalArgumentException("status event next status must be REVOKED or SUPERSEDED");
        }
        if (supersedingCredentialId != null && supersedingCredentialId <= 0) {
            throw new IllegalArgumentException("superseding credential id must be positive");
        }
        if ((nextStatus == CredentialStatus.SUPERSEDED) != (supersedingCredentialId != null)
                || !approvalDeadline.isAfter(effectiveAt)) {
            throw new IllegalArgumentException("superseding credential is required only for SUPERSEDED events");
        }
        this.credentialId = credentialId;
        this.issuerKeyId = issuerKeyId;
        this.previousStatus = previousStatus;
        this.nextStatus = nextStatus;
        this.reasonCode = reasonCode;
        this.reasonDetail = reasonDetail;
        this.actorUserId = actorUserId;
        this.supersedingCredentialId = supersedingCredentialId;
        this.approvalNonce = approvalNonce;
        this.approvalDeadline = approvalDeadline;
        this.idempotencyKey = idempotencyKey;
        this.effectiveAt = effectiveAt;
    }

    public static AncCredentialStatusEvent request(
            Long credentialId,
            Long issuerKeyId,
            CredentialStatus previousStatus,
            CredentialStatus nextStatus,
            String reasonCode,
            String reasonDetail,
            Long actorUserId,
            Long supersedingCredentialId,
            long approvalNonce,
            LocalDateTime approvalDeadline,
            String idempotencyKey,
            LocalDateTime effectiveAt) {
        return new AncCredentialStatusEvent(
                credentialId, issuerKeyId, previousStatus, nextStatus, reasonCode, reasonDetail, actorUserId,
                supersedingCredentialId, approvalNonce, approvalDeadline, idempotencyKey, effectiveAt);
    }

    public byte[] getApprovalDigest() {
        return approvalDigest == null ? null : approvalDigest.clone();
    }

    public byte[] getIssuerSignature() {
        return issuerSignature == null ? null : issuerSignature.clone();
    }

    public void recordApproval(String approvalPayloadJson, byte[] approvalDigest, byte[] issuerSignature) {
        if (this.approvalDigest != null || approvalDigest == null || approvalDigest.length != 32
                || issuerSignature == null || issuerSignature.length != 65 || approvalPayloadJson == null
                || approvalPayloadJson.isBlank()) {
            throw new IllegalStateException("status approval can only be recorded once with a valid signature");
        }
        this.approvalPayloadJson = approvalPayloadJson;
        this.approvalDigest = approvalDigest.clone();
        this.issuerSignature = issuerSignature.clone();
    }

    public void renewApproval(long approvalNonce, LocalDateTime approvalDeadline, LocalDateTime effectiveAt) {
        if (approvalNonce < 0 || approvalDeadline == null || effectiveAt == null
                || !approvalDeadline.isAfter(effectiveAt)) {
            throw new IllegalArgumentException("renewed status approval timestamps are invalid");
        }
        this.approvalNonce = approvalNonce;
        this.approvalDeadline = approvalDeadline;
        this.effectiveAt = effectiveAt;
        this.approvalPayloadJson = null;
        this.approvalDigest = null;
        this.issuerSignature = null;
    }
}
