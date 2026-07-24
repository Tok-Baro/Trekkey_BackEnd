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
        name = "anc_batch",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_anc_batch_public_id", columnNames = "public_id"),
                @UniqueConstraint(name = "uk_anc_batch_id_hash", columnNames = "batch_id_hash"),
                @UniqueConstraint(name = "uk_anc_batch_approval_digest", columnNames = "approval_digest")
        },
        indexes = {
                @Index(name = "idx_anc_batch_issuer_status", columnList = "issuer_organization_id,status"),
                @Index(name = "idx_anc_batch_issuer_key", columnList = "issuer_key_id")
        })
public class AncBatch extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "issuer_organization_id", nullable = false, updatable = false)
    private Long issuerOrganizationId;

    @Column(name = "issuer_key_id", nullable = false, updatable = false)
    private Long issuerKeyId;

    @Column(name = "public_id", nullable = false, length = 64, updatable = false)
    private String publicId;

    @Getter(AccessLevel.NONE)
    @Column(name = "batch_id_hash", nullable = false, length = 32, updatable = false)
    private byte[] batchIdHash;

    @Getter(AccessLevel.NONE)
    @Column(name = "schema_version_hash", nullable = false, length = 32, updatable = false)
    private byte[] schemaVersionHash;

    @Column(name = "tree_version", nullable = false, updatable = false)
    private int treeVersion;

    @Column(name = "leaf_count", nullable = false, updatable = false)
    private int leafCount;

    @Getter(AccessLevel.NONE)
    @Column(name = "merkle_root", nullable = false, length = 32, updatable = false)
    private byte[] merkleRoot;

    @Column(name = "approval_nonce", nullable = false)
    private long approvalNonce;

    @Column(name = "approval_deadline", nullable = false)
    private LocalDateTime approvalDeadline;

    @Lob
    @Column(name = "approval_payload_json")
    private String approvalPayloadJson;

    @Getter(AccessLevel.NONE)
    @Column(name = "approval_digest", length = 32, unique = true)
    private byte[] approvalDigest;

    @Getter(AccessLevel.NONE)
    @Column(name = "issuer_signature", length = 65)
    private byte[] issuerSignature;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BatchStatus status;

    @Column(name = "sealed_at", nullable = false, updatable = false)
    private LocalDateTime sealedAt;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    private AncBatch(
            Long issuerOrganizationId,
            Long issuerKeyId,
            String publicId,
            byte[] batchIdHash,
            byte[] schemaVersionHash,
            int treeVersion,
            int leafCount,
            byte[] merkleRoot,
            long approvalNonce,
            LocalDateTime approvalDeadline,
            LocalDateTime sealedAt) {
        if (issuerOrganizationId == null || issuerOrganizationId <= 0 || issuerKeyId == null || issuerKeyId <= 0
                || isBlank(publicId) || treeVersion < 1 || leafCount < 1
                || approvalNonce < 0 || approvalDeadline == null || sealedAt == null) {
            throw new IllegalArgumentException("sealed batch required fields are missing");
        }
        if (!approvalDeadline.isAfter(sealedAt)) {
            throw new IllegalArgumentException("approvalDeadline must be after sealedAt");
        }
        requireHash("batchIdHash", batchIdHash);
        requireHash("schemaVersionHash", schemaVersionHash);
        requireHash("merkleRoot", merkleRoot);
        this.issuerOrganizationId = issuerOrganizationId;
        this.issuerKeyId = issuerKeyId;
        this.publicId = publicId;
        this.batchIdHash = batchIdHash.clone();
        this.schemaVersionHash = schemaVersionHash.clone();
        this.treeVersion = treeVersion;
        this.leafCount = leafCount;
        this.merkleRoot = merkleRoot.clone();
        this.approvalNonce = approvalNonce;
        this.approvalDeadline = approvalDeadline;
        this.status = BatchStatus.SEALED;
        this.sealedAt = sealedAt;
    }

    public static AncBatch seal(
            Long issuerOrganizationId,
            Long issuerKeyId,
            String publicId,
            byte[] batchIdHash,
            byte[] schemaVersionHash,
            int treeVersion,
            int leafCount,
            byte[] merkleRoot,
            long approvalNonce,
            LocalDateTime approvalDeadline,
            LocalDateTime sealedAt) {
        return new AncBatch(
                issuerOrganizationId, issuerKeyId, publicId, batchIdHash, schemaVersionHash, treeVersion, leafCount,
                merkleRoot, approvalNonce, approvalDeadline, sealedAt);
    }

    public byte[] getBatchIdHash() {
        return copy(batchIdHash);
    }

    public byte[] getSchemaVersionHash() {
        return copy(schemaVersionHash);
    }

    public byte[] getMerkleRoot() {
        return copy(merkleRoot);
    }

    public byte[] getApprovalDigest() {
        return copy(approvalDigest);
    }

    public byte[] getIssuerSignature() {
        return copy(issuerSignature);
    }

    public void recordApproval(String approvalPayloadJson, byte[] approvalDigest, byte[] issuerSignature, LocalDateTime signedAt) {
        if (status != BatchStatus.SEALED || this.approvalDigest != null || approvalPayloadJson == null
                || approvalDigest == null || approvalDigest.length != 32 || issuerSignature == null
                || issuerSignature.length != 65 || signedAt == null || isBlank(approvalPayloadJson)
                || signedAt.isBefore(sealedAt) || signedAt.isAfter(approvalDeadline)) {
            throw new IllegalStateException("batch approval can only be recorded once for a sealed batch");
        }
        this.approvalPayloadJson = approvalPayloadJson;
        this.approvalDigest = approvalDigest.clone();
        this.issuerSignature = issuerSignature.clone();
        this.signedAt = signedAt;
        this.status = BatchStatus.SIGNED;
    }

    public void beginAnchoring() {
        transition(BatchStatus.SIGNED, BatchStatus.ANCHORING);
    }

    public void markAnchored() {
        transition(BatchStatus.ANCHORING, BatchStatus.ANCHORED);
    }

    public void markFailed() {
        if (status != BatchStatus.SIGNED && status != BatchStatus.ANCHORING) {
            throw new IllegalStateException("only a signed or anchoring batch can fail");
        }
        this.status = BatchStatus.FAILED;
    }

    public void reconcileAnchored() {
        if (status != BatchStatus.FAILED) {
            throw new IllegalStateException("only a failed batch can be reconciled as anchored");
        }
        this.status = BatchStatus.ANCHORED;
    }

    public void renewApproval(long approvalNonce, LocalDateTime approvalDeadline) {
        boolean unsignedSealed = status == BatchStatus.SEALED && approvalDigest == null;
        boolean failedSigned = status == BatchStatus.FAILED && approvalDigest != null;
        if ((!unsignedSealed && !failedSigned) || approvalNonce < 0 || approvalDeadline == null
                || !approvalDeadline.isAfter(sealedAt)) {
            throw new IllegalStateException("batch approval cannot be renewed in its current state");
        }
        this.approvalNonce = approvalNonce;
        this.approvalDeadline = approvalDeadline;
        this.approvalPayloadJson = null;
        this.approvalDigest = null;
        this.issuerSignature = null;
        this.signedAt = null;
        this.status = BatchStatus.SEALED;
    }

    private void transition(BatchStatus expected, BatchStatus next) {
        if (status != expected) {
            throw new IllegalStateException("batch transition from " + status + " to " + next + " is not allowed");
        }
        this.status = next;
    }

    private static void requireHash(String name, byte[] hash) {
        if (hash == null || hash.length != 32) {
            throw new IllegalArgumentException(name + " must be 32 bytes");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static byte[] copy(byte[] value) {
        return value == null ? null : value.clone();
    }
}
