package com.api.trekkey.domain.credential.entity;

import com.api.trekkey.global.entity.BaseEntity;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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
        name = "anc_credential",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_anc_credential_public_id", columnNames = "public_id"),
                @UniqueConstraint(name = "uk_anc_credential_id_hash", columnNames = "credential_id_hash"),
                @UniqueConstraint(
                        name = "uk_anc_credential_issuer_number",
                        columnNames = {"issuer_organization_id", "credential_no"})
        },
        indexes = {
                @Index(name = "idx_anc_credential_issuer_status", columnList = "issuer_organization_id,status"),
                @Index(name = "idx_anc_credential_ready", columnList = "status,created_at")
        })
public class AncCredential extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "issuer_organization_id", nullable = false, updatable = false)
    private Long issuerOrganizationId;

    @Column(name = "public_id", nullable = false, length = 64, updatable = false)
    private String publicId;

    @Getter(AccessLevel.NONE)
    @Column(name = "credential_id_hash", nullable = false, length = 32, updatable = false)
    private byte[] credentialIdHash;

    @Column(name = "credential_no", nullable = false, length = 100, updatable = false)
    private String credentialNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "credential_type", nullable = false, length = 20, updatable = false)
    private CredentialType credentialType;

    @Column(name = "schema_profile_id", nullable = false, length = 255, updatable = false)
    private String schemaProfileId;

    @Getter(AccessLevel.NONE)
    @Column(name = "schema_version_hash", nullable = false, length = 32, updatable = false)
    private byte[] schemaVersionHash;

    @Lob
    @Column(columnDefinition = "MEDIUMTEXT", name = "payload_json", nullable = false, updatable = false)
    private String payloadJson;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Getter(AccessLevel.NONE)
    @Column(columnDefinition = "MEDIUMBLOB", name = "canonical_bytes", nullable = false, updatable = false)
    private byte[] canonicalBytes;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Getter(AccessLevel.NONE)
    @Column(columnDefinition = "MEDIUMBLOB", name = "file_manifest_canonical_bytes", nullable = false, updatable = false)
    private byte[] fileManifestCanonicalBytes;

    @Getter(AccessLevel.NONE)
    @Column(name = "content_hash", nullable = false, length = 32, updatable = false)
    private byte[] contentHash;

    @Getter(AccessLevel.NONE)
    @Column(name = "file_manifest_hash", nullable = false, length = 32, updatable = false)
    private byte[] fileManifestHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CredentialStatus status;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", updatable = false)
    private LocalDateTime expiresAt;

    private AncCredential(
            Long issuerOrganizationId,
            String publicId,
            byte[] credentialIdHash,
            String credentialNo,
            CredentialType credentialType,
            String schemaProfileId,
            byte[] schemaVersionHash,
            String payloadJson,
            byte[] canonicalBytes,
            byte[] fileManifestCanonicalBytes,
            byte[] contentHash,
            byte[] fileManifestHash,
            LocalDateTime issuedAt,
            LocalDateTime expiresAt) {
        requireHash("credentialIdHash", credentialIdHash);
        requireHash("schemaVersionHash", schemaVersionHash);
        requireHash("contentHash", contentHash);
        requireHash("fileManifestHash", fileManifestHash);
        if (issuerOrganizationId == null || issuerOrganizationId <= 0 || isBlank(publicId)
                || isBlank(credentialNo) || credentialType == null || isBlank(schemaProfileId)
                || isBlank(payloadJson) || canonicalBytes == null || canonicalBytes.length == 0
                || fileManifestCanonicalBytes == null || fileManifestCanonicalBytes.length == 0 || issuedAt == null) {
            throw new IllegalArgumentException("credential immutable fields are required");
        }
        if (expiresAt != null && !expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
        this.issuerOrganizationId = issuerOrganizationId;
        this.publicId = publicId;
        this.credentialIdHash = credentialIdHash.clone();
        this.credentialNo = credentialNo;
        this.credentialType = credentialType;
        this.schemaProfileId = schemaProfileId;
        this.schemaVersionHash = schemaVersionHash.clone();
        this.payloadJson = payloadJson;
        this.canonicalBytes = canonicalBytes.clone();
        this.fileManifestCanonicalBytes = fileManifestCanonicalBytes.clone();
        this.contentHash = contentHash.clone();
        this.fileManifestHash = fileManifestHash.clone();
        this.status = CredentialStatus.READY;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public static AncCredential ready(
            Long issuerOrganizationId,
            String publicId,
            byte[] credentialIdHash,
            String credentialNo,
            CredentialType credentialType,
            String schemaProfileId,
            byte[] schemaVersionHash,
            String payloadJson,
            byte[] canonicalBytes,
            byte[] fileManifestCanonicalBytes,
            byte[] contentHash,
            byte[] fileManifestHash,
            LocalDateTime issuedAt,
            LocalDateTime expiresAt) {
        return new AncCredential(
                issuerOrganizationId, publicId, credentialIdHash, credentialNo, credentialType, schemaProfileId,
                schemaVersionHash, payloadJson, canonicalBytes, fileManifestCanonicalBytes, contentHash,
                fileManifestHash, issuedAt, expiresAt);
    }

    public byte[] getCredentialIdHash() {
        return copy(credentialIdHash);
    }

    public byte[] getSchemaVersionHash() {
        return copy(schemaVersionHash);
    }

    public byte[] getCanonicalBytes() {
        return copy(canonicalBytes);
    }

    public byte[] getFileManifestCanonicalBytes() {
        return copy(fileManifestCanonicalBytes);
    }

    public byte[] getContentHash() {
        return copy(contentHash);
    }

    public byte[] getFileManifestHash() {
        return copy(fileManifestHash);
    }

    public void markBatched() {
        transition(CredentialStatus.READY, CredentialStatus.BATCHED);
    }

    public void markAnchored() {
        transition(CredentialStatus.BATCHED, CredentialStatus.ANCHORED);
    }

    public void markRevoked() {
        transition(CredentialStatus.ANCHORED, CredentialStatus.REVOKED);
    }

    public void markSuperseded() {
        transition(CredentialStatus.ANCHORED, CredentialStatus.SUPERSEDED);
    }

    private void transition(CredentialStatus expected, CredentialStatus next) {
        if (status != expected) {
            throw new IllegalStateException("credential transition from " + status + " to " + next + " is not allowed");
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
