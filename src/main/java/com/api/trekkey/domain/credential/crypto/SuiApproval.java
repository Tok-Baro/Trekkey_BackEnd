package com.api.trekkey.domain.credential.crypto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Institution approvals bound to a Sui network, package and registry object.
 *
 * <p>This is not an EIP-712 domain or a Sui transaction signature. It reuses the existing
 * approval struct encoding and {@link Eip712#signDigest}/{@link Eip712#recoverSigner}
 * for the institution's recoverable, low-s secp256k1 signature.</p>
 */
public final class SuiApproval {

    public static final String SCHEME = "TREKKEY_SUI_APPROVAL_V1";
    public static final String SIGNATURE_SCHEME = "secp256k1-recoverable-low-s";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private SuiApproval() {
    }

    public static Hash32 domainHash(Domain domain) {
        require(domain, "Sui approval domain");
        return Hashing.keccak256(HexCodec.concat(
            SCHEME.getBytes(StandardCharsets.UTF_8),
            HexCodec.decode("0x" + domain.chainIdentifier(), 4, "chainIdentifier"),
            Hash32.fromHex(domain.packageId()).bytes(),
            Hash32.fromHex(domain.registryId()).bytes()
        ));
    }

    public static Hash32 batchDigest(Domain domain, Eip712.BatchApproval approval) {
        return digest(domainHash(domain), Eip712.batchStructHash(approval));
    }

    public static Hash32 statusDigest(Domain domain, Eip712.StatusApproval approval) {
        return digest(domainHash(domain), Eip712.statusStructHash(approval));
    }

    public static String batchPayloadJson(Domain domain, Eip712.BatchApproval approval) {
        ObjectNode root = payloadRoot(domain, "BatchApproval", batchDigest(domain, approval));
        ObjectNode message = root.putObject("message");
        message.put("issuerId", approval.issuerId().hex());
        message.put("batchIdHash", approval.batchIdHash().hex());
        message.put("merkleRoot", approval.merkleRoot().hex());
        message.put("schemaVersionHash", approval.schemaVersionHash().hex());
        message.put("leafCount", approval.leafCount().toString());
        message.put("treeVersion", approval.treeVersion().toString());
        message.put("issuerKeyVersion", approval.issuerKeyVersion().toString());
        message.put("approvalNonce", approval.approvalNonce().toString());
        message.put("deadline", approval.deadline().toString());
        return json(root);
    }

    public static String statusPayloadJson(Domain domain, Eip712.StatusApproval approval) {
        ObjectNode root = payloadRoot(domain, "StatusApproval", statusDigest(domain, approval));
        ObjectNode message = root.putObject("message");
        message.put("issuerId", approval.issuerId().hex());
        message.put("credentialIdHash", approval.credentialIdHash().hex());
        message.put("action", approval.action().value().toString());
        message.put("replacementCredentialIdHash", approval.replacementCredentialIdHash().hex());
        message.put("effectiveAt", approval.effectiveAt().toString());
        message.put("issuerKeyVersion", approval.issuerKeyVersion().toString());
        message.put("approvalNonce", approval.approvalNonce().toString());
        message.put("deadline", approval.deadline().toString());
        return json(root);
    }

    private static Hash32 digest(Hash32 domainHash, Hash32 structHash) {
        return Hashing.keccak256(HexCodec.concat(
            new byte[] {0x19, 0x01}, domainHash.bytes(), structHash.bytes()
        ));
    }

    private static ObjectNode payloadRoot(Domain domain, String primaryType, Hash32 digest) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("scheme", SCHEME);
        root.put("signatureScheme", SIGNATURE_SCHEME);
        ObjectNode domainNode = root.putObject("domain");
        domainNode.put("chainIdentifier", domain.chainIdentifier());
        domainNode.put("packageId", domain.packageId());
        domainNode.put("registryId", domain.registryId());
        root.put("primaryType", primaryType);
        root.put("digestHex", digest.hex());
        return root;
    }

    private static String json(ObjectNode node) {
        try {
            return OBJECT_MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("could not write Sui institution approval payload", exception);
        }
    }

    private static String normalizeChainIdentifier(String value) {
        if (value == null || !value.matches("(?:0[xX])?[0-9a-fA-F]{8}")) {
            throw new CryptoValidationException("chainIdentifier must contain exactly 4 bytes of hex");
        }
        return (value.length() == 10 ? value.substring(2) : value).toLowerCase(Locale.ROOT);
    }

    private static String normalizeObjectId(String value, String fieldName) {
        if (value == null || !value.matches("0x[0-9a-fA-F]{64}")) {
            throw new CryptoValidationException(fieldName + " must be a 0x-prefixed 32-byte hex value");
        }
        Hash32 id = Hash32.fromHex(value);
        if (id.isZero()) {
            throw new CryptoValidationException(fieldName + " must not be zero");
        }
        return id.hex();
    }

    private static void require(Object value, String fieldName) {
        if (value == null) {
            throw new CryptoValidationException(fieldName + " must not be null");
        }
    }

    /** Canonical chain identifier has no prefix; object IDs retain their 0x prefix. */
    public record Domain(String chainIdentifier, String packageId, String registryId) {
        public Domain {
            chainIdentifier = normalizeChainIdentifier(chainIdentifier);
            packageId = normalizeObjectId(packageId, "packageId");
            registryId = normalizeObjectId(registryId, "registryId");
        }
    }
}
