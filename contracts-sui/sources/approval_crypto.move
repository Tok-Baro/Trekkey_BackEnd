/// Trekkey's immutable V1 leaf and approval encoding. No credential plaintext is stored here.
module trekkey::approval_crypto;

use std::bcs;
use sui::ecdsa_k1;
use sui::hash;

const EInvalidHashLength: u64 = 0;
const EInvalidChainIdentifier: u64 = 1;
const EInvalidSignature: u64 = 2;
const EInvalidAction: u64 = 3;
const HALF_CURVE_ORDER: vector<u8> = x"7fffffffffffffffffffffffffffffff5d576e7357a4501ddfe92f46681b20a0";

/// Exact six bytes32 ABI words, double Keccak, as in the existing Java/OpenZeppelin fixture.
public fun hash_leaf(
    issuer_id: vector<u8>,
    credential_id_hash: vector<u8>,
    schema_version_hash: vector<u8>,
    content_hash: vector<u8>,
    file_manifest_hash: vector<u8>,
): vector<u8> {
    let mut encoded = hash::keccak256(&b"TREKKEY_CREDENTIAL_LEAF_V1");
    append_hash(&mut encoded, issuer_id);
    append_hash(&mut encoded, credential_id_hash);
    append_hash(&mut encoded, schema_version_hash);
    append_hash(&mut encoded, content_hash);
    append_hash(&mut encoded, file_manifest_hash);
    hash::keccak256(&hash::keccak256(&encoded))
}

public fun hash_pair(first: vector<u8>, second: vector<u8>): vector<u8> {
    require_hash(&first);
    require_hash(&second);
    let mut encoded;
    if (bytes_less_or_equal(&first, &second)) {
        encoded = first;
        encoded.append(second);
    } else {
        encoded = second;
        encoded.append(first);
    };
    hash::keccak256(&encoded)
}

public fun verify_proof(root: vector<u8>, leaf: vector<u8>, proof: vector<vector<u8>>): bool {
    require_hash(&root);
    require_hash(&leaf);
    let mut computed = leaf;
    let mut i = 0;
    while (i < proof.length()) {
        computed = hash_pair(computed, proof[i]);
        i = i + 1;
    };
    computed == root
}

/// Preserves the existing EIP-712 struct hash; the enclosing domain is Sui-specific.
public fun batch_struct_hash(
    issuer_id: vector<u8>, batch_id_hash: vector<u8>, merkle_root: vector<u8>,
    schema_version_hash: vector<u8>, leaf_count: u32, tree_version: u16,
    issuer_key_version: u64, approval_nonce: u64, deadline: u64,
): vector<u8> {
    let mut encoded = hash::keccak256(&b"BatchApproval(bytes32 issuerId,bytes32 batchIdHash,bytes32 merkleRoot,bytes32 schemaVersionHash,uint32 leafCount,uint16 treeVersion,uint64 issuerKeyVersion,uint64 approvalNonce,uint64 deadline)");
    append_hash(&mut encoded, issuer_id);
    append_hash(&mut encoded, batch_id_hash);
    append_hash(&mut encoded, merkle_root);
    append_hash(&mut encoded, schema_version_hash);
    append_uint_word(&mut encoded, leaf_count as u64);
    append_uint_word(&mut encoded, tree_version as u64);
    append_uint_word(&mut encoded, issuer_key_version);
    append_uint_word(&mut encoded, approval_nonce);
    append_uint_word(&mut encoded, deadline);
    hash::keccak256(&encoded)
}

public fun status_struct_hash(
    issuer_id: vector<u8>, credential_id_hash: vector<u8>, action: u8,
    replacement_credential_id_hash: vector<u8>, effective_at: u64,
    issuer_key_version: u64, approval_nonce: u64, deadline: u64,
): vector<u8> {
    assert!(action <= 1, EInvalidAction);
    let mut encoded = hash::keccak256(&b"StatusApproval(bytes32 issuerId,bytes32 credentialIdHash,uint8 action,bytes32 replacementCredentialIdHash,uint64 effectiveAt,uint64 issuerKeyVersion,uint64 approvalNonce,uint64 deadline)");
    append_hash(&mut encoded, issuer_id);
    append_hash(&mut encoded, credential_id_hash);
    append_uint_word(&mut encoded, action as u64);
    append_hash(&mut encoded, replacement_credential_id_hash);
    append_uint_word(&mut encoded, effective_at);
    append_uint_word(&mut encoded, issuer_key_version);
    append_uint_word(&mut encoded, approval_nonce);
    append_uint_word(&mut encoded, deadline);
    hash::keccak256(&encoded)
}

/// Four raw chain-identifier bytes, then both complete 32-byte Sui identities; no EVM truncation.
public fun domain_separator(
    chain_identifier: vector<u8>, package_id: address, registry_id: address,
): vector<u8> {
    assert!(chain_identifier.length() == 4, EInvalidChainIdentifier);
    let mut encoded = b"TREKKEY_SUI_APPROVAL_V1";
    encoded.append(chain_identifier);
    encoded.append(bcs::to_bytes(&package_id));
    encoded.append(bcs::to_bytes(&registry_id));
    hash::keccak256(&encoded)
}

/// The recovery native hashes its input, so pass this 66-byte preimage, NOT its 32-byte digest.
public fun approval_message(domain_hash: vector<u8>, struct_hash: vector<u8>): vector<u8> {
    let mut message = x"1901";
    append_hash(&mut message, domain_hash);
    append_hash(&mut message, struct_hash);
    message
}

public fun recover_ethereum_signer(message: &vector<u8>, signature: vector<u8>): vector<u8> {
    assert!(signature.length() == 65, EInvalidSignature);
    let mut normalized = signature;
    let recovery = normalized[64];
    assert!(recovery == 27 || recovery == 28, EInvalidSignature);
    let s = slice(&normalized, 32, 64);
    let half_curve_order = HALF_CURVE_ORDER;
    assert!(is_nonzero(&s) && bytes_less_or_equal(&s, &half_curve_order), EInvalidSignature);
    assert!(is_nonzero(&slice(&normalized, 0, 32)), EInvalidSignature);
    *normalized.borrow_mut(64) = recovery - 27;
    let compressed = ecdsa_k1::secp256k1_ecrecover(&normalized, message, 0);
    let uncompressed = ecdsa_k1::decompress_pubkey(&compressed);
    assert!(uncompressed.length() == 65 && uncompressed[0] == 4, EInvalidSignature);
    let hash = hash::keccak256(&slice(&uncompressed, 1, 65));
    slice(&hash, 12, 32)
}

public fun require_hash(value: &vector<u8>) {
    assert!(value.length() == 32, EInvalidHashLength);
}

public fun is_nonzero(value: &vector<u8>): bool {
    let mut i = 0;
    while (i < value.length()) {
        if (value[i] != 0) return true;
        i = i + 1;
    };
    false
}

fun append_hash(target: &mut vector<u8>, hash: vector<u8>) {
    require_hash(&hash);
    target.append(hash);
}

/// ABI unsigned integers are big-endian and left-padded to 32 bytes (unlike BCS integers).
fun append_uint_word(target: &mut vector<u8>, value: u64) {
    let mut i = 0u64;
    while (i < 24) { target.push_back(0); i = i + 1; };
    i = 0;
    while (i < 8) {
        target.push_back(((value >> (((7 - i) * 8) as u8)) & 255) as u8);
        i = i + 1;
    };
}

fun bytes_less_or_equal(first: &vector<u8>, second: &vector<u8>): bool {
    assert!(first.length() == second.length(), EInvalidHashLength);
    let mut i = 0;
    while (i < first.length()) {
        if (first[i] < second[i]) return true;
        if (first[i] > second[i]) return false;
        i = i + 1;
    };
    true
}

fun slice(bytes: &vector<u8>, start: u64, end: u64): vector<u8> {
    let mut result = vector[];
    let mut i = start;
    while (i < end) { result.push_back(bytes[i]); i = i + 1; };
    result
}
