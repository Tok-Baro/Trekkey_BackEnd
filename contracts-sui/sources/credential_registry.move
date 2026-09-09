/// Hash-only credential registry. Issuer approval and transaction-relayer authority are separate.
module trekkey::credential_registry;

use std::type_name;
use sui::clock::{Self, Clock};
use sui::event;
use sui::hash;
use sui::table::{Self, Table};
use trekkey::approval_crypto as crypto;

const EInvalidCap: u64 = 0;
const EInvalidInput: u64 = 1;
const ENotRelayer: u64 = 2;
const EPaused: u64 = 3;
const EIssuerExists: u64 = 4;
const EIssuerNotFound: u64 = 5;
const EIssuerInactive: u64 = 6;
const EIssuerCompromised: u64 = 7;
const EApprovalExpired: u64 = 8;
const EApprovalUsed: u64 = 9;
const ENonceUsed: u64 = 10;
const EWrongSigner: u64 = 11;
const EBatchExists: u64 = 12;
const EStatusExists: u64 = 13;
const EInvalidTimestamp: u64 = 14;
const EKeyAlreadyRetired: u64 = 15;
const EKeyAlreadyCompromised: u64 = 16;

public struct Registry has key {
    id: UID,
    chain_identifier: vector<u8>,
    package_id: address,
    paused: bool,
    issuer_keys: Table<IssuerKeyId, IssuerKey>,
    batches: Table<vector<u8>, Batch>,
    statuses: Table<CredentialKey, Status>,
    used_nonces: Table<IssuerNonce, bool>,
    used_digests: Table<vector<u8>, bool>,
    relayers: Table<address, bool>,
}

/// Registry-scoped administration; ordinary possession of a relayer address cannot alter keys.
public struct AdminCap has key, store { id: UID, registry_id: ID }
public struct IssuerKeyId has copy, drop, store { issuer_id: vector<u8>, key_version: u64 }
public struct CredentialKey has copy, drop, store { issuer_id: vector<u8>, credential_id_hash: vector<u8> }
public struct IssuerNonce has copy, drop, store { issuer_id: vector<u8>, nonce: u64 }

public struct IssuerKey has copy, drop, store {
    signer: vector<u8>, valid_from: u64, valid_until: u64, compromised_at: u64,
}
public struct Batch has copy, drop, store {
    issuer_id: vector<u8>, merkle_root: vector<u8>, schema_version_hash: vector<u8>,
    leaf_count: u32, tree_version: u16, issuer_key_version: u64, anchored_at: u64,
}
public struct Status has copy, drop, store {
    state: u8, effective_at: u64, recorded_at: u64, issuer_key_version: u64,
    replacement_credential_id_hash: vector<u8>,
}

public struct RegistryCreated has copy, drop {
    registry_id: ID, package_id: address, chain_identifier: vector<u8>, admin: address,
}
public struct IssuerKeyRegistered has copy, drop {
    registry_id: ID, issuer_id: vector<u8>, key_version: u64, signer: vector<u8>, valid_from: u64,
}
public struct IssuerKeyRetired has copy, drop {
    registry_id: ID, issuer_id: vector<u8>, key_version: u64, valid_until: u64,
}
public struct IssuerKeyCompromised has copy, drop {
    registry_id: ID, issuer_id: vector<u8>, key_version: u64, compromised_at: u64,
}
public struct RelayerChanged has copy, drop { registry_id: ID, relayer: address, allowed: bool }
public struct PauseChanged has copy, drop { registry_id: ID, paused: bool }
public struct BatchAnchored has copy, drop {
    registry_id: ID, issuer_id: vector<u8>, batch_id_hash: vector<u8>, merkle_root: vector<u8>,
    schema_version_hash: vector<u8>, leaf_count: u32, tree_version: u16,
    issuer_key_version: u64, anchored_at: u64,
}
public struct CredentialRevoked has copy, drop {
    registry_id: ID, issuer_id: vector<u8>, credential_id_hash: vector<u8>,
    effective_at: u64, issuer_key_version: u64,
}
public struct CredentialSuperseded has copy, drop {
    registry_id: ID, issuer_id: vector<u8>, credential_id_hash: vector<u8>,
    replacement_credential_id_hash: vector<u8>, effective_at: u64, issuer_key_version: u64,
}

/// The deployment operator must bind the configured four bytes to the actual RPC chain identifier.
/// A new deployment cannot impersonate the trusted registry: both package and object IDs are signed.
/// Deliberately delivers the registry-scoped cap to its creator; later transfers remain explicit.
#[allow(lint(self_transfer))]
public fun create_registry(chain_identifier: vector<u8>, ctx: &mut TxContext) {
    let (registry, cap) = new_registry(chain_identifier, ctx);
    event::emit(RegistryCreated {
        registry_id: object::id(&registry), package_id: registry.package_id,
        chain_identifier: registry.chain_identifier, admin: ctx.sender(),
    });
    transfer::share_object(registry);
    transfer::public_transfer(cap, ctx.sender());
}

fun new_registry(chain_identifier: vector<u8>, ctx: &mut TxContext): (Registry, AdminCap) {
    assert!(chain_identifier.length() == 4, EInvalidInput);
    let registry = Registry {
        id: object::new(ctx), chain_identifier, package_id: type_name::original_id<Registry>(),
        paused: false, issuer_keys: table::new(ctx), batches: table::new(ctx),
        statuses: table::new(ctx), used_nonces: table::new(ctx),
        used_digests: table::new(ctx), relayers: table::new(ctx),
    };
    let cap = AdminCap { id: object::new(ctx), registry_id: object::id(&registry) };
    (registry, cap)
}

public fun set_relayer(registry: &mut Registry, cap: &AdminCap, relayer: address, allowed: bool) {
    require_cap(registry, cap);
    assert!(relayer != @0x0, EInvalidInput);
    if (registry.relayers.contains(relayer)) { registry.relayers.remove(relayer); };
    if (allowed) { registry.relayers.add(relayer, true); };
    event::emit(RelayerChanged { registry_id: object::id(registry), relayer, allowed });
}

public fun set_paused(registry: &mut Registry, cap: &AdminCap, paused: bool) {
    require_cap(registry, cap);
    registry.paused = paused;
    event::emit(PauseChanged { registry_id: object::id(registry), paused });
}

public fun register_issuer_key(
    registry: &mut Registry, cap: &AdminCap, clock: &Clock,
    issuer_id: vector<u8>, key_version: u64, signer: vector<u8>,
) {
    require_cap(registry, cap);
    require_nonzero_hash(&issuer_id);
    assert!(key_version > 0 && signer.length() == 20 && crypto::is_nonzero(&signer), EInvalidInput);
    let key = IssuerKeyId { issuer_id, key_version };
    assert!(!registry.issuer_keys.contains(key), EIssuerExists);
    let valid_from = seconds(clock);
    registry.issuer_keys.add(key, IssuerKey { signer, valid_from, valid_until: 0, compromised_at: 0 });
    event::emit(IssuerKeyRegistered { registry_id: object::id(registry), issuer_id, key_version, signer, valid_from });
}

public fun retire_issuer_key(
    registry: &mut Registry, cap: &AdminCap, clock: &Clock,
    issuer_id: vector<u8>, key_version: u64, valid_until: u64,
) {
    require_cap(registry, cap);
    let key = IssuerKeyId { issuer_id, key_version };
    assert!(registry.issuer_keys.contains(key), EIssuerNotFound);
    let issuer_key = registry.issuer_keys.borrow_mut(key);
    assert!(issuer_key.valid_until == 0, EKeyAlreadyRetired);
    assert!(valid_until > 0 && valid_until >= issuer_key.valid_from && valid_until <= seconds(clock), EInvalidTimestamp);
    issuer_key.valid_until = valid_until;
    event::emit(IssuerKeyRetired { registry_id: object::id(registry), issuer_id, key_version, valid_until });
}

public fun compromise_issuer_key(
    registry: &mut Registry, cap: &AdminCap, clock: &Clock,
    issuer_id: vector<u8>, key_version: u64, compromised_at: u64,
) {
    require_cap(registry, cap);
    let key = IssuerKeyId { issuer_id, key_version };
    assert!(registry.issuer_keys.contains(key), EIssuerNotFound);
    let issuer_key = registry.issuer_keys.borrow_mut(key);
    assert!(issuer_key.compromised_at == 0, EKeyAlreadyCompromised);
    assert!(compromised_at > 0 && compromised_at >= issuer_key.valid_from && compromised_at <= seconds(clock), EInvalidTimestamp);
    issuer_key.compromised_at = compromised_at;
    event::emit(IssuerKeyCompromised { registry_id: object::id(registry), issuer_id, key_version, compromised_at });
}

public fun anchor_batch(
    registry: &mut Registry, clock: &Clock,
    issuer_id: vector<u8>, batch_id_hash: vector<u8>, merkle_root: vector<u8>,
    schema_version_hash: vector<u8>, leaf_count: u32, tree_version: u16,
    issuer_key_version: u64, approval_nonce: u64, deadline: u64,
    issuer_signature: vector<u8>, ctx: &TxContext,
) {
    require_relayer(registry, ctx);
    assert!(!registry.paused, EPaused);
    require_nonzero_hash(&issuer_id);
    require_nonzero_hash(&batch_id_hash);
    require_nonzero_hash(&merkle_root);
    require_nonzero_hash(&schema_version_hash);
    assert!(leaf_count > 0 && tree_version == 1 && issuer_key_version > 0, EInvalidInput);
    let struct_hash = crypto::batch_struct_hash(
        issuer_id, batch_id_hash, merkle_root, schema_version_hash,
        leaf_count, tree_version, issuer_key_version, approval_nonce, deadline,
    );
    consume_approval(registry, clock, issuer_id, issuer_key_version, approval_nonce, deadline, struct_hash, issuer_signature);
    assert!(!registry.batches.contains(batch_id_hash), EBatchExists);
    let anchored_at = seconds(clock);
    registry.batches.add(batch_id_hash, Batch {
        issuer_id, merkle_root, schema_version_hash, leaf_count,
        tree_version, issuer_key_version, anchored_at,
    });
    event::emit(BatchAnchored {
        registry_id: object::id(registry), issuer_id, batch_id_hash, merkle_root,
        schema_version_hash, leaf_count, tree_version, issuer_key_version, anchored_at,
    });
}

public fun revoke_credential(
    registry: &mut Registry, clock: &Clock, issuer_id: vector<u8>, credential_id_hash: vector<u8>,
    replacement_credential_id_hash: vector<u8>, effective_at: u64,
    issuer_key_version: u64, approval_nonce: u64, deadline: u64,
    issuer_signature: vector<u8>, ctx: &TxContext,
) {
    set_status(registry, clock, issuer_id, credential_id_hash, 0, replacement_credential_id_hash,
        effective_at, issuer_key_version, approval_nonce, deadline, issuer_signature, ctx);
}

public fun supersede_credential(
    registry: &mut Registry, clock: &Clock, issuer_id: vector<u8>, credential_id_hash: vector<u8>,
    replacement_credential_id_hash: vector<u8>, effective_at: u64,
    issuer_key_version: u64, approval_nonce: u64, deadline: u64,
    issuer_signature: vector<u8>, ctx: &TxContext,
) {
    set_status(registry, clock, issuer_id, credential_id_hash, 1, replacement_credential_id_hash,
        effective_at, issuer_key_version, approval_nonce, deadline, issuer_signature, ctx);
}

/// As in the EVM V1 contract, the issuer attests target/replacement membership offchain.
/// A root-only registry cannot prove replacement membership without an additional leaf/proof input.
fun set_status(
    registry: &mut Registry, clock: &Clock, issuer_id: vector<u8>, credential_id_hash: vector<u8>,
    action: u8, replacement_credential_id_hash: vector<u8>, effective_at: u64,
    issuer_key_version: u64, approval_nonce: u64, deadline: u64,
    issuer_signature: vector<u8>, ctx: &TxContext,
) {
    require_relayer(registry, ctx);
    require_nonzero_hash(&issuer_id);
    require_nonzero_hash(&credential_id_hash);
    crypto::require_hash(&replacement_credential_id_hash);
    assert!(effective_at > 0 && effective_at <= seconds(clock), EInvalidTimestamp);
    assert!(issuer_key_version > 0, EInvalidInput);
    if (action == 0) {
        assert!(!crypto::is_nonzero(&replacement_credential_id_hash), EInvalidInput);
    } else {
        assert!(crypto::is_nonzero(&replacement_credential_id_hash)
            && replacement_credential_id_hash != credential_id_hash, EInvalidInput);
    };
    let struct_hash = crypto::status_struct_hash(
        issuer_id, credential_id_hash, action, replacement_credential_id_hash,
        effective_at, issuer_key_version, approval_nonce, deadline,
    );
    consume_approval(registry, clock, issuer_id, issuer_key_version, approval_nonce, deadline, struct_hash, issuer_signature);
    let key = CredentialKey { issuer_id, credential_id_hash };
    assert!(!registry.statuses.contains(key), EStatusExists);
    registry.statuses.add(key, Status {
        state: action + 1, effective_at, recorded_at: seconds(clock),
        issuer_key_version, replacement_credential_id_hash,
    });
    if (action == 0) {
        event::emit(CredentialRevoked { registry_id: object::id(registry), issuer_id,
            credential_id_hash, effective_at, issuer_key_version });
    } else {
        event::emit(CredentialSuperseded { registry_id: object::id(registry), issuer_id,
            credential_id_hash, replacement_credential_id_hash, effective_at, issuer_key_version });
    };
}

fun consume_approval(
    registry: &mut Registry, clock: &Clock, issuer_id: vector<u8>, issuer_key_version: u64,
    approval_nonce: u64, deadline: u64, struct_hash: vector<u8>, issuer_signature: vector<u8>,
) {
    let now = seconds(clock);
    assert!(deadline >= now, EApprovalExpired);
    let key_id = IssuerKeyId { issuer_id, key_version: issuer_key_version };
    assert!(registry.issuer_keys.contains(key_id), EIssuerNotFound);
    let key = registry.issuer_keys.borrow(key_id);
    assert!(now >= key.valid_from && (key.valid_until == 0 || now <= key.valid_until), EIssuerInactive);
    assert!(key.compromised_at == 0 || now < key.compromised_at, EIssuerCompromised);
    let message = crypto::approval_message(domain_separator(registry), struct_hash);
    let digest = hash::keccak256(&message);
    assert!(!registry.used_digests.contains(digest), EApprovalUsed);
    let nonce_key = IssuerNonce { issuer_id, nonce: approval_nonce };
    assert!(!registry.used_nonces.contains(nonce_key), ENonceUsed);
    assert!(crypto::recover_ethereum_signer(&message, issuer_signature) == key.signer, EWrongSigner);
    registry.used_digests.add(digest, true);
    registry.used_nonces.add(nonce_key, true);
}

public fun domain_separator(registry: &Registry): vector<u8> {
    crypto::domain_separator(registry.chain_identifier, registry.package_id, object::id_address(registry))
}
public fun get_issuer_key(registry: &Registry, issuer_id: vector<u8>, key_version: u64): Option<IssuerKey> {
    let key = IssuerKeyId { issuer_id, key_version };
    if (registry.issuer_keys.contains(key)) option::some(*registry.issuer_keys.borrow(key)) else option::none()
}
public fun get_batch(registry: &Registry, batch_id_hash: vector<u8>): Option<Batch> {
    if (registry.batches.contains(batch_id_hash)) option::some(*registry.batches.borrow(batch_id_hash)) else option::none()
}
public fun get_status(registry: &Registry, issuer_id: vector<u8>, credential_id_hash: vector<u8>): Option<Status> {
    let key = CredentialKey { issuer_id, credential_id_hash };
    if (registry.statuses.contains(key)) option::some(*registry.statuses.borrow(key)) else option::none()
}
public fun nonce_used(registry: &Registry, issuer_id: vector<u8>, nonce: u64): bool {
    registry.used_nonces.contains(IssuerNonce { issuer_id, nonce })
}
public fun batch_root(batch: &Batch): vector<u8> { batch.merkle_root }
public fun status_state(status: &Status): u8 { status.state }
public fun issuer_signer(key: &IssuerKey): vector<u8> { key.signer }
public fun is_relayer(registry: &Registry, relayer: address): bool { registry.relayers.contains(relayer) }
public fun is_paused(registry: &Registry): bool { registry.paused }

fun require_cap(registry: &Registry, cap: &AdminCap) {
    assert!(cap.registry_id == object::id(registry), EInvalidCap);
}
fun require_relayer(registry: &Registry, ctx: &TxContext) {
    assert!(registry.relayers.contains(ctx.sender()), ENotRelayer);
}
fun require_nonzero_hash(value: &vector<u8>) {
    crypto::require_hash(value);
    assert!(crypto::is_nonzero(value), EInvalidInput);
}
fun seconds(clock: &Clock): u64 { clock::timestamp_ms(clock) / 1000 }

#[test_only]
public fun new_for_testing(chain_identifier: vector<u8>, ctx: &mut TxContext): (Registry, AdminCap) {
    new_registry(chain_identifier, ctx)
}
#[test_only]
public fun destroy_for_testing(registry: Registry, cap: AdminCap) {
    let Registry { id, chain_identifier: _, package_id: _, paused: _, issuer_keys, batches,
        statuses, used_nonces, used_digests, relayers } = registry;
    table::drop(issuer_keys); table::drop(batches); table::drop(statuses);
    table::drop(used_nonces); table::drop(used_digests); table::drop(relayers);
    id.delete();
    let AdminCap { id, registry_id: _ } = cap;
    id.delete();
}
