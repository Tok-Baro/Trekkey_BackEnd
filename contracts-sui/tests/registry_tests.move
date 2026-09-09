#[test_only]
module trekkey::registry_tests;

use sui::clock::{Self, Clock};
use sui::ecdsa_k1;
use sui::test_scenario::{Self, Scenario};
use trekkey::approval_crypto as crypto;
use trekkey::credential_registry::{Self as registry, Registry, AdminCap};

const RELAYER: address = @0xa;
const ISSUER: vector<u8> = x"3333333333333333333333333333333333333333333333333333333333333333";
const BATCH: vector<u8> = x"4444444444444444444444444444444444444444444444444444444444444444";
const OTHER: vector<u8> = x"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
const ROOT: vector<u8> = x"5555555555555555555555555555555555555555555555555555555555555555";
const SCHEMA: vector<u8> = x"6666666666666666666666666666666666666666666666666666666666666666";
const CREDENTIAL: vector<u8> = x"7777777777777777777777777777777777777777777777777777777777777777";
const REPLACEMENT: vector<u8> = x"8888888888888888888888888888888888888888888888888888888888888888";
const ZERO: vector<u8> = x"0000000000000000000000000000000000000000000000000000000000000000";
// Public synthetic fixtures only. The signing native is test-only and cannot run onchain.
const TEST_SCALAR: vector<u8> = x"0000000000000000000000000000000000000000000000000000000000000001";
const TEST_SIGNER: vector<u8> = x"7e5f4552091a69125d5dfcb7b8c2659029395bdf";

fun setup(): (Scenario, Registry, AdminCap, Clock) {
    let mut scenario = test_scenario::begin(RELAYER);
    let (mut reg, cap) = registry::new_for_testing(x"a1b2c3d4", scenario.ctx());
    let mut clock = clock::create_for_testing(scenario.ctx());
    clock::set_for_testing(&mut clock, 100000);
    registry::set_relayer(&mut reg, &cap, RELAYER, true);
    registry::register_issuer_key(&mut reg, &cap, &clock, ISSUER, 1, TEST_SIGNER);
    (scenario, reg, cap, clock)
}

fun finish(scenario: Scenario, reg: Registry, cap: AdminCap, clock: Clock) {
    registry::destroy_for_testing(reg, cap);
    clock::destroy_for_testing(clock);
    scenario.end();
}

fun sign_message(message: vector<u8>): vector<u8> {
    let private_key = TEST_SCALAR;
    let mut signature = ecdsa_k1::secp256k1_sign(&private_key, &message, 0, true);
    let v = signature[64] + 27;
    *signature.borrow_mut(64) = v;
    signature
}

fun batch_struct(batch: vector<u8>, nonce: u64, deadline: u64): vector<u8> {
    crypto::batch_struct_hash(ISSUER, batch, ROOT, SCHEMA, 3, 1, 1, nonce, deadline)
}

fun batch_signature(reg: &Registry, batch: vector<u8>, nonce: u64, deadline: u64): vector<u8> {
    sign_message(crypto::approval_message(registry::domain_separator(reg), batch_struct(batch, nonce, deadline)))
}

fun status_signature(
    reg: &Registry, credential: vector<u8>, action: u8, replacement: vector<u8>,
    effective_at: u64, nonce: u64,
): vector<u8> {
    let hash = crypto::status_struct_hash(ISSUER, credential, action, replacement, effective_at, 1, nonce, 200);
    sign_message(crypto::approval_message(registry::domain_separator(reg), hash))
}

fun anchor(reg: &mut Registry, clock: &Clock, scenario: &mut Scenario, batch: vector<u8>, nonce: u64) {
    let signature = batch_signature(reg, batch, nonce, 200);
    registry::anchor_batch(reg, clock, ISSUER, batch, ROOT, SCHEMA, 3, 1, 1, nonce, 200, signature, scenario.ctx());
}

#[test]
fun issuer_approved_batch_and_absence_getters() {
    let (mut scenario, mut reg, cap, clock) = setup();
    assert!(registry::get_batch(&reg, BATCH).is_none(), 0);
    assert!(registry::get_status(&reg, ISSUER, CREDENTIAL).is_none(), 1);
    assert!(registry::get_issuer_key(&reg, ISSUER, 2).is_none(), 2);
    anchor(&mut reg, &clock, &mut scenario, BATCH, 7);
    let batch = registry::get_batch(&reg, BATCH).destroy_some();
    assert!(registry::batch_root(&batch) == ROOT, 3);
    assert!(registry::nonce_used(&reg, ISSUER, 7), 4);
    assert!(registry::issuer_signer(&registry::get_issuer_key(&reg, ISSUER, 1).destroy_some()) == TEST_SIGNER, 5);
    finish(scenario, reg, cap, clock);
}

#[test]
fun pause_preserves_revoke_and_supersede_and_key_rotation_preserves_roots() {
    let (mut scenario, mut reg, cap, mut clock) = setup();
    anchor(&mut reg, &clock, &mut scenario, BATCH, 7);
    registry::set_paused(&mut reg, &cap, true);
    let signature = status_signature(&reg, CREDENTIAL, 0, ZERO, 100, 8);
    registry::revoke_credential(&mut reg, &clock, ISSUER, CREDENTIAL, ZERO, 100, 1, 8, 200, signature, scenario.ctx());
    let signature = status_signature(&reg, OTHER, 1, REPLACEMENT, 100, 9);
    registry::supersede_credential(&mut reg, &clock, ISSUER, OTHER, REPLACEMENT, 100, 1, 9, 200, signature, scenario.ctx());
    assert!(registry::status_state(&registry::get_status(&reg, ISSUER, CREDENTIAL).destroy_some()) == 1, 0);
    assert!(registry::status_state(&registry::get_status(&reg, ISSUER, OTHER).destroy_some()) == 2, 1);
    registry::retire_issuer_key(&mut reg, &cap, &clock, ISSUER, 1, 100);
    clock::set_for_testing(&mut clock, 101000);
    registry::register_issuer_key(&mut reg, &cap, &clock, ISSUER, 2, TEST_SIGNER);
    assert!(registry::batch_root(&registry::get_batch(&reg, BATCH).destroy_some()) == ROOT, 2);
    assert!(registry::is_paused(&reg), 3);
    finish(scenario, reg, cap, clock);
}

#[test]
fun nonces_are_scoped_to_issuer() {
    let (mut scenario, mut reg, cap, clock) = setup();
    anchor(&mut reg, &clock, &mut scenario, BATCH, 7);
    registry::register_issuer_key(&mut reg, &cap, &clock, OTHER, 1, TEST_SIGNER);
    let hash = crypto::batch_struct_hash(OTHER, OTHER, ROOT, SCHEMA, 3, 1, 1, 7, 200);
    let signature = sign_message(crypto::approval_message(registry::domain_separator(&reg), hash));
    registry::anchor_batch(&mut reg, &clock, OTHER, OTHER, ROOT, SCHEMA, 3, 1, 1, 7, 200, signature, scenario.ctx());
    assert!(registry::nonce_used(&reg, OTHER, 7), 0);
    finish(scenario, reg, cap, clock);
}

#[test]
#[expected_failure(abort_code = 2, location = trekkey::credential_registry)]
fun rejects_removed_relayer() {
    let (mut scenario, mut reg, cap, clock) = setup();
    registry::set_relayer(&mut reg, &cap, RELAYER, false);
    anchor(&mut reg, &clock, &mut scenario, BATCH, 7);
    finish(scenario, reg, cap, clock);
}

#[test]
#[expected_failure(abort_code = 0, location = trekkey::credential_registry)]
fun rejects_cap_from_another_registry() {
    let (mut scenario, reg, cap, clock) = setup();
    let (mut other, other_cap) = registry::new_for_testing(x"a1b2c3d4", scenario.ctx());
    registry::set_relayer(&mut other, &cap, RELAYER, true);
    registry::destroy_for_testing(other, other_cap);
    finish(scenario, reg, cap, clock);
}

#[test]
#[expected_failure(abort_code = 4, location = trekkey::credential_registry)]
fun rejects_overwriting_registered_issuer_key() {
    let (scenario, mut reg, cap, clock) = setup();
    registry::register_issuer_key(&mut reg, &cap, &clock, ISSUER, 1, TEST_SIGNER);
    finish(scenario, reg, cap, clock);
}

#[test]
#[expected_failure(abort_code = 3, location = trekkey::credential_registry)]
fun rejects_anchor_while_paused() {
    let (mut scenario, mut reg, cap, clock) = setup();
    registry::set_paused(&mut reg, &cap, true);
    anchor(&mut reg, &clock, &mut scenario, BATCH, 7);
    finish(scenario, reg, cap, clock);
}

#[test]
#[expected_failure(abort_code = 11, location = trekkey::credential_registry)]
fun rejects_root_changed_after_signature() {
    let (mut scenario, mut reg, cap, clock) = setup();
    let signature = batch_signature(&reg, BATCH, 7, 200);
    registry::anchor_batch(&mut reg, &clock, ISSUER, BATCH, OTHER, SCHEMA, 3, 1, 1, 7, 200, signature, scenario.ctx());
    finish(scenario, reg, cap, clock);
}

#[test]
#[expected_failure(abort_code = 8, location = trekkey::credential_registry)]
fun rejects_expired_approval() {
    let (mut scenario, mut reg, cap, clock) = setup();
    let signature = batch_signature(&reg, BATCH, 7, 99);
    registry::anchor_batch(&mut reg, &clock, ISSUER, BATCH, ROOT, SCHEMA, 3, 1, 1, 7, 99, signature, scenario.ctx());
    finish(scenario, reg, cap, clock);
}

#[test]
#[expected_failure(abort_code = 9, location = trekkey::credential_registry)]
fun rejects_exact_digest_replay() {
    let (mut scenario, mut reg, cap, clock) = setup();
    anchor(&mut reg, &clock, &mut scenario, BATCH, 7);
    anchor(&mut reg, &clock, &mut scenario, BATCH, 7);
    finish(scenario, reg, cap, clock);
}

#[test]
#[expected_failure(abort_code = 10, location = trekkey::credential_registry)]
fun rejects_nonce_reuse_on_different_batch() {
    let (mut scenario, mut reg, cap, clock) = setup();
    anchor(&mut reg, &clock, &mut scenario, BATCH, 7);
    anchor(&mut reg, &clock, &mut scenario, OTHER, 7);
    finish(scenario, reg, cap, clock);
}

#[test]
#[expected_failure(abort_code = 10, location = trekkey::credential_registry)]
fun rejects_batch_nonce_reuse_for_status() {
    let (mut scenario, mut reg, cap, clock) = setup();
    anchor(&mut reg, &clock, &mut scenario, BATCH, 7);
    let signature = status_signature(&reg, CREDENTIAL, 0, ZERO, 100, 7);
    registry::revoke_credential(&mut reg, &clock, ISSUER, CREDENTIAL, ZERO, 100, 1, 7, 200, signature, scenario.ctx());
    finish(scenario, reg, cap, clock);
}

#[test]
#[expected_failure(abort_code = 12, location = trekkey::credential_registry)]
fun rejects_separately_approved_duplicate_batch() {
    let (mut scenario, mut reg, cap, clock) = setup();
    anchor(&mut reg, &clock, &mut scenario, BATCH, 7);
    anchor(&mut reg, &clock, &mut scenario, BATCH, 8);
    finish(scenario, reg, cap, clock);
}

#[test]
#[expected_failure(abort_code = 11, location = trekkey::credential_registry)]
fun rejects_approval_from_another_registry() {
    let (mut scenario, mut reg, cap, clock) = setup();
    let (other, other_cap) = registry::new_for_testing(x"a1b2c3d4", scenario.ctx());
    let signature = batch_signature(&other, BATCH, 7, 200);
    registry::destroy_for_testing(other, other_cap);
    registry::anchor_batch(&mut reg, &clock, ISSUER, BATCH, ROOT, SCHEMA, 3, 1, 1, 7, 200, signature, scenario.ctx());
    finish(scenario, reg, cap, clock);
}

#[test]
#[expected_failure(abort_code = 11, location = trekkey::credential_registry)]
fun rejects_approval_from_another_chain() {
    let (mut scenario, mut reg, cap, clock) = setup();
    let domain = crypto::domain_separator(x"a1b2c3d5", @trekkey, object::id_address(&reg));
    let signature = sign_message(crypto::approval_message(domain, batch_struct(BATCH, 7, 200)));
    registry::anchor_batch(&mut reg, &clock, ISSUER, BATCH, ROOT, SCHEMA, 3, 1, 1, 7, 200, signature, scenario.ctx());
    finish(scenario, reg, cap, clock);
}

#[test]
#[expected_failure(abort_code = 11, location = trekkey::credential_registry)]
fun rejects_approval_from_another_package() {
    let (mut scenario, mut reg, cap, clock) = setup();
    let domain = crypto::domain_separator(x"a1b2c3d4", @0x123, object::id_address(&reg));
    let signature = sign_message(crypto::approval_message(domain, batch_struct(BATCH, 7, 200)));
    registry::anchor_batch(&mut reg, &clock, ISSUER, BATCH, ROOT, SCHEMA, 3, 1, 1, 7, 200, signature, scenario.ctx());
    finish(scenario, reg, cap, clock);
}

#[test]
#[expected_failure(abort_code = 2, location = trekkey::approval_crypto)]
fun rejects_external_recovery_id_zero() {
    let (mut scenario, mut reg, cap, clock) = setup();
    let mut signature = batch_signature(&reg, BATCH, 7, 200);
    *signature.borrow_mut(64) = 0;
    registry::anchor_batch(&mut reg, &clock, ISSUER, BATCH, ROOT, SCHEMA, 3, 1, 1, 7, 200, signature, scenario.ctx());
    finish(scenario, reg, cap, clock);
}

#[test]
#[expected_failure(abort_code = 2, location = trekkey::approval_crypto)]
fun rejects_high_s_signature() {
    let (mut scenario, mut reg, cap, clock) = setup();
    let mut signature = batch_signature(&reg, BATCH, 7, 200);
    let mut i = 32u64;
    while (i < 64) { *signature.borrow_mut(i) = 255; i = i + 1; };
    registry::anchor_batch(&mut reg, &clock, ISSUER, BATCH, ROOT, SCHEMA, 3, 1, 1, 7, 200, signature, scenario.ctx());
    finish(scenario, reg, cap, clock);
}

#[test]
#[expected_failure(abort_code = 6, location = trekkey::credential_registry)]
fun rejects_retired_key() {
    let (mut scenario, mut reg, cap, mut clock) = setup();
    registry::retire_issuer_key(&mut reg, &cap, &clock, ISSUER, 1, 100);
    clock::set_for_testing(&mut clock, 101000);
    anchor(&mut reg, &clock, &mut scenario, BATCH, 7);
    finish(scenario, reg, cap, clock);
}

#[test]
#[expected_failure(abort_code = 7, location = trekkey::credential_registry)]
fun rejects_compromised_key() {
    let (mut scenario, mut reg, cap, clock) = setup();
    registry::compromise_issuer_key(&mut reg, &cap, &clock, ISSUER, 1, 100);
    anchor(&mut reg, &clock, &mut scenario, BATCH, 7);
    finish(scenario, reg, cap, clock);
}

#[test]
#[expected_failure(abort_code = 14, location = trekkey::credential_registry)]
fun rejects_future_status_time() {
    let (mut scenario, mut reg, cap, clock) = setup();
    let signature = status_signature(&reg, CREDENTIAL, 0, ZERO, 101, 8);
    registry::revoke_credential(&mut reg, &clock, ISSUER, CREDENTIAL, ZERO, 101, 1, 8, 200, signature, scenario.ctx());
    finish(scenario, reg, cap, clock);
}

#[test]
#[expected_failure(abort_code = 14, location = trekkey::credential_registry)]
fun rejects_zero_status_time() {
    let (mut scenario, mut reg, cap, clock) = setup();
    let signature = status_signature(&reg, CREDENTIAL, 0, ZERO, 0, 8);
    registry::revoke_credential(&mut reg, &clock, ISSUER, CREDENTIAL, ZERO, 0, 1, 8, 200, signature, scenario.ctx());
    finish(scenario, reg, cap, clock);
}

#[test]
#[expected_failure(abort_code = 13, location = trekkey::credential_registry)]
fun rejects_status_overwrite_with_a_new_approval() {
    let (mut scenario, mut reg, cap, clock) = setup();
    let signature = status_signature(&reg, CREDENTIAL, 0, ZERO, 100, 8);
    registry::revoke_credential(&mut reg, &clock, ISSUER, CREDENTIAL, ZERO, 100, 1, 8, 200, signature, scenario.ctx());
    let signature = status_signature(&reg, CREDENTIAL, 1, REPLACEMENT, 100, 9);
    registry::supersede_credential(&mut reg, &clock, ISSUER, CREDENTIAL, REPLACEMENT, 100, 1, 9, 200, signature, scenario.ctx());
    finish(scenario, reg, cap, clock);
}

#[test]
#[expected_failure(abort_code = 1, location = trekkey::credential_registry)]
fun rejects_revoke_with_replacement() {
    let (mut scenario, mut reg, cap, clock) = setup();
    let signature = status_signature(&reg, CREDENTIAL, 0, REPLACEMENT, 100, 8);
    registry::revoke_credential(&mut reg, &clock, ISSUER, CREDENTIAL, REPLACEMENT, 100, 1, 8, 200, signature, scenario.ctx());
    finish(scenario, reg, cap, clock);
}

#[test]
#[expected_failure(abort_code = 1, location = trekkey::credential_registry)]
fun rejects_superseding_with_itself() {
    let (mut scenario, mut reg, cap, clock) = setup();
    let signature = status_signature(&reg, CREDENTIAL, 1, CREDENTIAL, 100, 8);
    registry::supersede_credential(&mut reg, &clock, ISSUER, CREDENTIAL, CREDENTIAL, 100, 1, 8, 200, signature, scenario.ctx());
    finish(scenario, reg, cap, clock);
}

#[test]
#[expected_failure(abort_code = 1, location = trekkey::credential_registry)]
fun rejects_unsupported_tree_version() {
    let (mut scenario, mut reg, cap, clock) = setup();
    let signature = batch_signature(&reg, BATCH, 7, 200);
    registry::anchor_batch(&mut reg, &clock, ISSUER, BATCH, ROOT, SCHEMA, 3, 2, 1, 7, 200, signature, scenario.ctx());
    finish(scenario, reg, cap, clock);
}

#[test]
#[expected_failure(abort_code = 1, location = trekkey::credential_registry)]
fun rejects_empty_batch() {
    let (mut scenario, mut reg, cap, clock) = setup();
    let signature = batch_signature(&reg, BATCH, 7, 200);
    registry::anchor_batch(&mut reg, &clock, ISSUER, BATCH, ROOT, SCHEMA, 0, 1, 1, 7, 200, signature, scenario.ctx());
    finish(scenario, reg, cap, clock);
}

#[test]
fun accepts_approval_at_exact_deadline_second() {
    let (mut scenario, mut reg, cap, clock) = setup();
    let signature = batch_signature(&reg, BATCH, 7, 100);
    registry::anchor_batch(&mut reg, &clock, ISSUER, BATCH, ROOT, SCHEMA, 3, 1, 1, 7, 100, signature, scenario.ctx());
    assert!(registry::get_batch(&reg, BATCH).is_some(), 0);
    finish(scenario, reg, cap, clock);
}

#[test]
fun retirement_valid_until_is_inclusive_as_in_evm_v1() {
    let (mut scenario, mut reg, cap, clock) = setup();
    registry::retire_issuer_key(&mut reg, &cap, &clock, ISSUER, 1, 100);
    anchor(&mut reg, &clock, &mut scenario, BATCH, 7);
    assert!(registry::get_batch(&reg, BATCH).is_some(), 0);
    finish(scenario, reg, cap, clock);
}

#[test]
#[expected_failure(abort_code = 10, location = trekkey::credential_registry)]
fun rejects_nonce_reuse_after_key_rotation() {
    let (mut scenario, mut reg, cap, clock) = setup();
    anchor(&mut reg, &clock, &mut scenario, BATCH, 7);
    registry::register_issuer_key(&mut reg, &cap, &clock, ISSUER, 2, TEST_SIGNER);
    let hash = crypto::batch_struct_hash(ISSUER, OTHER, ROOT, SCHEMA, 3, 1, 2, 7, 200);
    let signature = sign_message(crypto::approval_message(registry::domain_separator(&reg), hash));
    registry::anchor_batch(&mut reg, &clock, ISSUER, OTHER, ROOT, SCHEMA, 3, 1, 2, 7, 200, signature, scenario.ctx());
    finish(scenario, reg, cap, clock);
}

#[test]
#[expected_failure(abort_code = 5, location = trekkey::credential_registry)]
fun rejects_unregistered_key_version() {
    let (mut scenario, mut reg, cap, clock) = setup();
    let hash = crypto::batch_struct_hash(ISSUER, BATCH, ROOT, SCHEMA, 3, 1, 2, 7, 200);
    let signature = sign_message(crypto::approval_message(registry::domain_separator(&reg), hash));
    registry::anchor_batch(&mut reg, &clock, ISSUER, BATCH, ROOT, SCHEMA, 3, 1, 2, 7, 200, signature, scenario.ctx());
    finish(scenario, reg, cap, clock);
}

#[test]
#[expected_failure(abort_code = 11, location = trekkey::credential_registry)]
fun rejects_a_signature_by_a_different_registered_signer() {
    let (mut scenario, mut reg, cap, clock) = setup();
    registry::register_issuer_key(&mut reg, &cap, &clock, ISSUER, 2, x"1111111111111111111111111111111111111111");
    let hash = crypto::batch_struct_hash(ISSUER, BATCH, ROOT, SCHEMA, 3, 1, 2, 7, 200);
    let signature = sign_message(crypto::approval_message(registry::domain_separator(&reg), hash));
    registry::anchor_batch(&mut reg, &clock, ISSUER, BATCH, ROOT, SCHEMA, 3, 1, 2, 7, 200, signature, scenario.ctx());
    finish(scenario, reg, cap, clock);
}

#[test]
#[expected_failure(abort_code = 1, location = trekkey::credential_registry)]
fun rejects_wrong_chain_identifier_length() {
    let mut scenario = test_scenario::begin(RELAYER);
    let (reg, cap) = registry::new_for_testing(x"a1b2c3", scenario.ctx());
    registry::destroy_for_testing(reg, cap);
    scenario.end();
}

#[test]
#[expected_failure(abort_code = 2, location = trekkey::approval_crypto)]
fun rejects_truncated_signature() {
    let (mut scenario, mut reg, cap, clock) = setup();
    let mut signature = batch_signature(&reg, BATCH, 7, 200);
    signature.pop_back();
    registry::anchor_batch(&mut reg, &clock, ISSUER, BATCH, ROOT, SCHEMA, 3, 1, 1, 7, 200, signature, scenario.ctx());
    finish(scenario, reg, cap, clock);
}

#[test]
#[expected_failure(abort_code = 0, location = trekkey::approval_crypto)]
fun rejects_non_bytes32_merkle_root() {
    let (mut scenario, mut reg, cap, clock) = setup();
    let signature = batch_signature(&reg, BATCH, 7, 200);
    registry::anchor_batch(&mut reg, &clock, ISSUER, BATCH, x"55", SCHEMA, 3, 1, 1, 7, 200, signature, scenario.ctx());
    finish(scenario, reg, cap, clock);
}

#[test]
#[expected_failure(abort_code = 1, location = trekkey::credential_registry)]
fun rejects_zero_issuer_identity() {
    let (scenario, mut reg, cap, clock) = setup();
    registry::register_issuer_key(&mut reg, &cap, &clock, ZERO, 1, TEST_SIGNER);
    finish(scenario, reg, cap, clock);
}

#[test]
#[expected_failure(abort_code = 14, location = trekkey::credential_registry)]
fun rejects_future_retirement_time() {
    let (scenario, mut reg, cap, clock) = setup();
    registry::retire_issuer_key(&mut reg, &cap, &clock, ISSUER, 1, 101);
    finish(scenario, reg, cap, clock);
}
