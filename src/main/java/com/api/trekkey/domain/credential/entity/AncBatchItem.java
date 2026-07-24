package com.api.trekkey.domain.credential.entity;

import com.api.trekkey.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "anc_batch_item",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_anc_batch_item_credential", columnNames = "credential_id"),
                @UniqueConstraint(name = "uk_anc_batch_item_leaf_index", columnNames = {"batch_id", "leaf_index"})
        },
        indexes = @Index(name = "idx_anc_batch_item_batch", columnList = "batch_id"))
public class AncBatchItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false, updatable = false)
    private Long batchId;

    @Column(name = "credential_id", nullable = false, updatable = false)
    private Long credentialId;

    @Column(name = "leaf_index", nullable = false, updatable = false)
    private int leafIndex;

    @Getter(AccessLevel.NONE)
    @Column(name = "credential_id_hash", nullable = false, length = 32, updatable = false)
    private byte[] credentialIdHash;

    @Getter(AccessLevel.NONE)
    @Column(name = "leaf_hash", nullable = false, length = 32, updatable = false)
    private byte[] leafHash;

    @Lob
    @Column(name = "merkle_proof_json", nullable = false, updatable = false)
    private String merkleProofJson;

    private AncBatchItem(
            Long batchId,
            Long credentialId,
            int leafIndex,
            byte[] credentialIdHash,
            byte[] leafHash,
            String merkleProofJson) {
        if (batchId == null || batchId <= 0 || credentialId == null || credentialId <= 0 || leafIndex < 0
                || isBlank(merkleProofJson)
                || credentialIdHash == null || credentialIdHash.length != 32 || leafHash == null || leafHash.length != 32) {
            throw new IllegalArgumentException("batch item immutable fields are required");
        }
        this.batchId = batchId;
        this.credentialId = credentialId;
        this.leafIndex = leafIndex;
        this.credentialIdHash = credentialIdHash.clone();
        this.leafHash = leafHash.clone();
        this.merkleProofJson = merkleProofJson;
    }

    public static AncBatchItem of(
            Long batchId,
            Long credentialId,
            int leafIndex,
            byte[] credentialIdHash,
            byte[] leafHash,
            String merkleProofJson) {
        return new AncBatchItem(batchId, credentialId, leafIndex, credentialIdHash, leafHash, merkleProofJson);
    }

    public byte[] getCredentialIdHash() {
        return copy(credentialIdHash);
    }

    public byte[] getLeafHash() {
        return copy(leafHash);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static byte[] copy(byte[] value) {
        return value == null ? null : value.clone();
    }
}
