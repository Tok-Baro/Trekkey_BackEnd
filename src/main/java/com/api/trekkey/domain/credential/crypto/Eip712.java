package com.api.trekkey.domain.credential.crypto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigInteger;
import java.security.SignatureException;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Sign;

/** Exact EIP-712 encoding used by TrekkeyCredentialRegistryV1. */
public final class Eip712 {

    public static final String DOMAIN_NAME = "TrekkeyCredentialRegistry";
    public static final String DOMAIN_VERSION = "1";
    public static final Hash32 DOMAIN_TYPEHASH = Hashing.keccak256Utf8(
        "EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)"
    );
    public static final Hash32 BATCH_APPROVAL_TYPEHASH = Hashing.keccak256Utf8(
        "BatchApproval(bytes32 issuerId,bytes32 batchIdHash,bytes32 merkleRoot,bytes32 schemaVersionHash,uint32 leafCount,uint16 treeVersion,uint64 issuerKeyVersion,uint64 approvalNonce,uint64 deadline)"
    );
    public static final Hash32 STATUS_APPROVAL_TYPEHASH = Hashing.keccak256Utf8(
        "StatusApproval(bytes32 issuerId,bytes32 credentialIdHash,uint8 action,bytes32 replacementCredentialIdHash,uint64 effectiveAt,uint64 issuerKeyVersion,uint64 approvalNonce,uint64 deadline)"
    );

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Hash32 NAME_HASH = Hashing.keccak256Utf8(DOMAIN_NAME);
    private static final Hash32 VERSION_HASH = Hashing.keccak256Utf8(DOMAIN_VERSION);

    private Eip712() {
    }

    public static Hash32 batchStructHash(BatchApproval approval) {
        require(approval, "batch approval");
        return Hashing.keccak256(AbiWords.encodeWords(
            BATCH_APPROVAL_TYPEHASH.bytes(),
            approval.issuerId().bytes(),
            approval.batchIdHash().bytes(),
            approval.merkleRoot().bytes(),
            approval.schemaVersionHash().bytes(),
            AbiWords.uint(approval.leafCount(), 32, "leafCount"),
            AbiWords.uint(approval.treeVersion(), 16, "treeVersion"),
            AbiWords.uint(approval.issuerKeyVersion(), 64, "issuerKeyVersion"),
            AbiWords.uint(approval.approvalNonce(), 64, "approvalNonce"),
            AbiWords.uint(approval.deadline(), 64, "deadline")
        ));
    }

    public static Hash32 statusStructHash(StatusApproval approval) {
        require(approval, "status approval");
        return Hashing.keccak256(AbiWords.encodeWords(
            STATUS_APPROVAL_TYPEHASH.bytes(),
            approval.issuerId().bytes(),
            approval.credentialIdHash().bytes(),
            AbiWords.uint(approval.action().value(), 8, "action"),
            approval.replacementCredentialIdHash().bytes(),
            AbiWords.uint(approval.effectiveAt(), 64, "effectiveAt"),
            AbiWords.uint(approval.issuerKeyVersion(), 64, "issuerKeyVersion"),
            AbiWords.uint(approval.approvalNonce(), 64, "approvalNonce"),
            AbiWords.uint(approval.deadline(), 64, "deadline")
        ));
    }

    public static Hash32 domainSeparator(Domain domain) {
        require(domain, "EIP-712 domain");
        return Hashing.keccak256(AbiWords.encodeWords(
            DOMAIN_TYPEHASH.bytes(),
            NAME_HASH.bytes(),
            VERSION_HASH.bytes(),
            AbiWords.uint256(domain.chainId(), "chainId"),
            AbiWords.address(domain.verifyingContract())
        ));
    }

    public static Hash32 batchDigest(Domain domain, BatchApproval approval) {
        return typedDataDigest(domainSeparator(domain), batchStructHash(approval));
    }

    public static Hash32 statusDigest(Domain domain, StatusApproval approval) {
        return typedDataDigest(domainSeparator(domain), statusStructHash(approval));
    }

    public static Signature65 signDigest(Hash32 digest, ECKeyPair keyPair) {
        require(digest, "digest");
        if (keyPair == null) {
            throw new CryptoValidationException("signing key must not be null");
        }
        Sign.SignatureData signature = Sign.signMessage(digest.bytes(), keyPair, false);
        return Signature65.of(HexCodec.concat(signature.getR(), signature.getS(), signature.getV()));
    }

    public static EthereumAddress recoverSigner(Hash32 digest, Signature65 signature) {
        require(digest, "digest");
        if (signature == null) {
            throw new CryptoValidationException("signature must not be null");
        }
        try {
            Sign.SignatureData signatureData = new Sign.SignatureData(signature.recoveryId(), signature.r(), signature.s());
            return EthereumAddress.fromPublicKey(Sign.signedMessageHashToKey(digest.bytes(), signatureData));
        } catch (SignatureException exception) {
            throw new CryptoValidationException("signature cannot be recovered", exception);
        }
    }

    public static String batchTypedDataJson(Domain domain, BatchApproval approval) {
        ObjectNode root = typedDataRoot(domain, "BatchApproval");
        addBatchTypes((ObjectNode) root.get("types"));
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

    public static String statusTypedDataJson(Domain domain, StatusApproval approval) {
        ObjectNode root = typedDataRoot(domain, "StatusApproval");
        addStatusTypes((ObjectNode) root.get("types"));
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

    private static ObjectNode typedDataRoot(Domain domain, String primaryType) {
        require(domain, "EIP-712 domain");
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        ObjectNode types = root.putObject("types");
        ArrayNode domainTypes = types.putArray("EIP712Domain");
        typeField(domainTypes, "name", "string");
        typeField(domainTypes, "version", "string");
        typeField(domainTypes, "chainId", "uint256");
        typeField(domainTypes, "verifyingContract", "address");
        ObjectNode domainNode = root.putObject("domain");
        domainNode.put("name", DOMAIN_NAME);
        domainNode.put("version", DOMAIN_VERSION);
        domainNode.put("chainId", domain.chainId().toString());
        domainNode.put("verifyingContract", domain.verifyingContract().hex());
        root.put("primaryType", primaryType);
        return root;
    }

    private static void addBatchTypes(ObjectNode types) {
        ArrayNode fields = types.putArray("BatchApproval");
        typeField(fields, "issuerId", "bytes32");
        typeField(fields, "batchIdHash", "bytes32");
        typeField(fields, "merkleRoot", "bytes32");
        typeField(fields, "schemaVersionHash", "bytes32");
        typeField(fields, "leafCount", "uint32");
        typeField(fields, "treeVersion", "uint16");
        typeField(fields, "issuerKeyVersion", "uint64");
        typeField(fields, "approvalNonce", "uint64");
        typeField(fields, "deadline", "uint64");
    }

    private static void addStatusTypes(ObjectNode types) {
        ArrayNode fields = types.putArray("StatusApproval");
        typeField(fields, "issuerId", "bytes32");
        typeField(fields, "credentialIdHash", "bytes32");
        typeField(fields, "action", "uint8");
        typeField(fields, "replacementCredentialIdHash", "bytes32");
        typeField(fields, "effectiveAt", "uint64");
        typeField(fields, "issuerKeyVersion", "uint64");
        typeField(fields, "approvalNonce", "uint64");
        typeField(fields, "deadline", "uint64");
    }

    private static void typeField(ArrayNode types, String name, String type) {
        ObjectNode field = types.addObject();
        field.put("name", name);
        field.put("type", type);
    }

    private static Hash32 typedDataDigest(Hash32 domainSeparator, Hash32 structHash) {
        return Hashing.keccak256(HexCodec.concat(new byte[] {0x19, 0x01}, domainSeparator.bytes(), structHash.bytes()));
    }

    private static String json(ObjectNode node) {
        try {
            return OBJECT_MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("could not write EIP-712 typed data", exception);
        }
    }

    private static void require(Object value, String fieldName) {
        if (value == null) {
            throw new CryptoValidationException(fieldName + " must not be null");
        }
    }

    public record Domain(BigInteger chainId, EthereumAddress verifyingContract) {
        public Domain {
            AbiWords.uint256(chainId, "chainId");
            if (chainId.signum() == 0) {
                throw new CryptoValidationException("chainId must be greater than zero");
            }
            require(verifyingContract, "verifyingContract");
        }
    }

    public record BatchApproval(
        Hash32 issuerId,
        Hash32 batchIdHash,
        Hash32 merkleRoot,
        Hash32 schemaVersionHash,
        BigInteger leafCount,
        BigInteger treeVersion,
        BigInteger issuerKeyVersion,
        BigInteger approvalNonce,
        BigInteger deadline
    ) {
        public BatchApproval {
            require(issuerId, "issuerId");
            require(batchIdHash, "batchIdHash");
            require(merkleRoot, "merkleRoot");
            require(schemaVersionHash, "schemaVersionHash");
            AbiWords.uint(leafCount, 32, "leafCount");
            AbiWords.uint(treeVersion, 16, "treeVersion");
            AbiWords.uint(issuerKeyVersion, 64, "issuerKeyVersion");
            AbiWords.uint(approvalNonce, 64, "approvalNonce");
            AbiWords.uint(deadline, 64, "deadline");
        }
    }

    public record StatusApproval(
        Hash32 issuerId,
        Hash32 credentialIdHash,
        StatusAction action,
        Hash32 replacementCredentialIdHash,
        BigInteger effectiveAt,
        BigInteger issuerKeyVersion,
        BigInteger approvalNonce,
        BigInteger deadline
    ) {
        public StatusApproval {
            require(issuerId, "issuerId");
            require(credentialIdHash, "credentialIdHash");
            require(action, "action");
            require(replacementCredentialIdHash, "replacementCredentialIdHash");
            AbiWords.uint(effectiveAt, 64, "effectiveAt");
            AbiWords.uint(issuerKeyVersion, 64, "issuerKeyVersion");
            AbiWords.uint(approvalNonce, 64, "approvalNonce");
            AbiWords.uint(deadline, 64, "deadline");
        }
    }

    public enum StatusAction {
        REVOKE(BigInteger.ZERO),
        SUPERSEDE(BigInteger.ONE);

        private final BigInteger value;

        StatusAction(BigInteger value) {
            this.value = value;
        }

        public BigInteger value() {
            return value;
        }
    }
}
