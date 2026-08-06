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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "anc_issuer_key",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_anc_issuer_key_organization_version",
                columnNames = {"organization_id", "key_version"}),
        indexes = {
                @Index(name = "idx_anc_issuer_key_organization_status", columnList = "organization_id,status")
        })
public class AncIssuerKey extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "key_version", nullable = false)
    private int keyVersion;

    @Getter(AccessLevel.NONE)
    @Column(name = "signer_address", nullable = false, length = 20, updatable = false)
    private byte[] signerAddress;

    @Column(name = "signer_ref", nullable = false, length = 255, updatable = false)
    private String signerRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IssuerKeyStatus status;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private LocalDateTime validFrom;

    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    @Column(name = "compromised_at")
    private LocalDateTime compromisedAt;

    private AncIssuerKey(
            Long organizationId,
            int keyVersion,
            byte[] signerAddress,
            String signerRef,
            LocalDateTime validFrom,
            LocalDateTime validUntil) {
        if (organizationId == null || organizationId <= 0 || keyVersion < 1
                || signerAddress == null || signerAddress.length != 20 || isBlank(signerRef) || validFrom == null) {
            throw new IllegalArgumentException("issuer key requires organization, positive version, and a 20-byte signer address");
        }
        if (validUntil != null && !validUntil.isAfter(validFrom)) {
            throw new IllegalArgumentException("validUntil must be after validFrom");
        }
        this.organizationId = organizationId;
        this.keyVersion = keyVersion;
        this.signerAddress = signerAddress.clone();
        this.signerRef = signerRef;
        this.status = IssuerKeyStatus.ACTIVE;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
    }

    public static AncIssuerKey activate(
            Long organizationId,
            int keyVersion,
            byte[] signerAddress,
            String signerRef,
            LocalDateTime validFrom,
            LocalDateTime validUntil) {
        return new AncIssuerKey(organizationId, keyVersion, signerAddress, signerRef, validFrom, validUntil);
    }

    public byte[] getSignerAddress() {
        return copy(signerAddress);
    }

    public void retire(LocalDateTime retiredAt) {
        if (status != IssuerKeyStatus.ACTIVE || retiredAt == null || retiredAt.isBefore(validFrom)) {
            throw new IllegalStateException("only an active issuer key can be retired");
        }
        this.status = IssuerKeyStatus.RETIRED;
        this.validUntil = retiredAt;
    }

    public void markCompromised(LocalDateTime detectedAt) {
        if ((status != IssuerKeyStatus.ACTIVE && status != IssuerKeyStatus.RETIRED)
                || detectedAt == null || detectedAt.isBefore(validFrom)) {
            throw new IllegalStateException("issuer key cannot be compromised at the requested time");
        }
        this.status = IssuerKeyStatus.COMPROMISED;
        this.compromisedAt = detectedAt;
    }

    public void synchronizeLifecycle(LocalDateTime onChainValidUntil, LocalDateTime onChainCompromisedAt) {
        if ((onChainValidUntil != null && onChainValidUntil.isBefore(validFrom))
                || (onChainCompromisedAt != null && onChainCompromisedAt.isBefore(validFrom))) {
            throw new IllegalStateException("on-chain issuer key lifecycle predates key registration");
        }
        if (validUntil != null && !validUntil.equals(onChainValidUntil)) {
            throw new IllegalStateException("on-chain issuer key retirement does not match the local ledger");
        }
        if (compromisedAt != null && !compromisedAt.equals(onChainCompromisedAt)) {
            throw new IllegalStateException("on-chain issuer key compromise does not match the local ledger");
        }
        if (status != IssuerKeyStatus.ACTIVE
                && onChainValidUntil == null
                && onChainCompromisedAt == null) {
            throw new IllegalStateException("on-chain issuer key lifecycle cannot be reverted");
        }
        if (onChainValidUntil != null && validUntil == null) {
            validUntil = onChainValidUntil;
            if (status == IssuerKeyStatus.ACTIVE) {
                status = IssuerKeyStatus.RETIRED;
            }
        }
        if (onChainCompromisedAt != null && compromisedAt == null) {
            compromisedAt = onChainCompromisedAt;
            status = IssuerKeyStatus.COMPROMISED;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static byte[] copy(byte[] value) {
        return value == null ? null : value.clone();
    }
}
