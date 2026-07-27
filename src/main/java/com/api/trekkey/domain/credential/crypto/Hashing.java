package com.api.trekkey.domain.credential.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.web3j.crypto.Hash;

public final class Hashing {

    public static final Hash32 ISSUER_ID_DOMAIN = keccak256Utf8("TREKKEY_ISSUER_ID_V1");
    public static final Hash32 CREDENTIAL_ID_DOMAIN = keccak256Utf8("TREKKEY_CREDENTIAL_ID_V1");
    public static final Hash32 BATCH_ID_DOMAIN = keccak256Utf8("TREKKEY_BATCH_ID_V1");

    private Hashing() {
    }

    public static Hash32 sha256(byte[] value) {
        try {
            return Hash32.of(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public static Hash32 keccak256(byte[] value) {
        return Hash32.of(Hash.sha3(value));
    }

    public static Hash32 keccak256Utf8(String value) {
        if (value == null) {
            throw new CryptoValidationException("hash input must not be null");
        }
        return keccak256(value.getBytes(StandardCharsets.UTF_8));
    }

    public static Hash32 issuerId(String organizationPublicId) {
        requireIdentifier(organizationPublicId, "organizationPublicId");
        return keccak256(AbiWords.encode(ISSUER_ID_DOMAIN, keccak256Utf8(organizationPublicId)));
    }

    public static Hash32 credentialId(Hash32 issuerId, String credentialPublicId) {
        if (issuerId == null) {
            throw new CryptoValidationException("issuerId must not be null");
        }
        requireIdentifier(credentialPublicId, "credentialPublicId");
        return keccak256(AbiWords.encode(CREDENTIAL_ID_DOMAIN, issuerId, keccak256Utf8(credentialPublicId)));
    }

    public static Hash32 batchId(Hash32 issuerId, String batchPublicId) {
        if (issuerId == null) {
            throw new CryptoValidationException("issuerId must not be null");
        }
        requireIdentifier(batchPublicId, "batchPublicId");
        return keccak256(AbiWords.encode(BATCH_ID_DOMAIN, issuerId, keccak256Utf8(batchPublicId)));
    }

    public static Hash32 schemaVersion(String schemaProfileAndVersion) {
        requireIdentifier(schemaProfileAndVersion, "schemaProfileAndVersion");
        return keccak256Utf8(schemaProfileAndVersion);
    }

    private static void requireIdentifier(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new CryptoValidationException(fieldName + " must not be blank");
        }
    }
}
