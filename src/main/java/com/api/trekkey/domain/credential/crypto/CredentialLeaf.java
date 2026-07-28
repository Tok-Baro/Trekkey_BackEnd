package com.api.trekkey.domain.credential.crypto;

/** The six bytes32 values hashed by TrekkeyCredentialRegistryV1.hashLeaf. */
public record CredentialLeaf(
    Hash32 issuerId,
    Hash32 credentialIdHash,
    Hash32 schemaVersionHash,
    Hash32 contentHash,
    Hash32 fileManifestHash
) {

    public static final Hash32 LEAF_DOMAIN = Hashing.keccak256Utf8("TREKKEY_CREDENTIAL_LEAF_V1");

    public CredentialLeaf {
        require(issuerId, "issuerId");
        require(credentialIdHash, "credentialIdHash");
        require(schemaVersionHash, "schemaVersionHash");
        require(contentHash, "contentHash");
        require(fileManifestHash, "fileManifestHash");
    }

    public byte[] abiEncodedValue() {
        return AbiWords.encode(LEAF_DOMAIN, issuerId, credentialIdHash, schemaVersionHash, contentHash, fileManifestHash);
    }

    public Hash32 hash() {
        return Hashing.keccak256(Hashing.keccak256(abiEncodedValue()).bytes());
    }

    private static void require(Hash32 value, String fieldName) {
        if (value == null) {
            throw new CryptoValidationException(fieldName + " must not be null");
        }
    }
}
