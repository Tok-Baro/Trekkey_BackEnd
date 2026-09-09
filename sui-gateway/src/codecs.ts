import { bcs } from '@mysten/sui/bcs';

// BCS field order is the published Move ABI, not JSON field order.
const vector = bcs.vector(bcs.u8());
const table = bcs.struct('Table', { id: bcs.Address, size: bcs.u64() });
export const Clock = bcs.struct('Clock', { id: bcs.Address, timestamp_ms: bcs.u64() });
export const Registry = bcs.struct('Registry', {
  id: bcs.Address, chain_identifier: vector, package_id: bcs.Address, paused: bcs.bool(),
  issuer_keys: table, batches: table, statuses: table, used_nonces: table, used_digests: table, relayers: table,
});
export const IssuerKeyId = bcs.struct('IssuerKeyId', { issuer_id: vector, key_version: bcs.u64() });
export const CredentialKey = bcs.struct('CredentialKey', { issuer_id: vector, credential_id_hash: vector });
export const IssuerKey = bcs.struct('IssuerKey', {
  signer: vector, valid_from: bcs.u64(), valid_until: bcs.u64(), compromised_at: bcs.u64(),
});
export const Batch = bcs.struct('Batch', {
  issuer_id: vector, merkle_root: vector, schema_version_hash: vector, leaf_count: bcs.u32(),
  tree_version: bcs.u16(), issuer_key_version: bcs.u64(), anchored_at: bcs.u64(),
});
export const Status = bcs.struct('Status', {
  state: bcs.u8(), effective_at: bcs.u64(), recorded_at: bcs.u64(), issuer_key_version: bcs.u64(),
  replacement_credential_id_hash: vector,
});
export const BatchAnchored = bcs.struct('BatchAnchored', {
  registry_id: bcs.Address, issuer_id: vector, batch_id_hash: vector, merkle_root: vector,
  schema_version_hash: vector, leaf_count: bcs.u32(), tree_version: bcs.u16(),
  issuer_key_version: bcs.u64(), anchored_at: bcs.u64(),
});
export const CredentialRevoked = bcs.struct('CredentialRevoked', {
  registry_id: bcs.Address, issuer_id: vector, credential_id_hash: vector, effective_at: bcs.u64(), issuer_key_version: bcs.u64(),
});
export const CredentialSuperseded = bcs.struct('CredentialSuperseded', {
  registry_id: bcs.Address, issuer_id: vector, credential_id_hash: vector,
  replacement_credential_id_hash: vector, effective_at: bcs.u64(), issuer_key_version: bcs.u64(),
});

export function parseBcs<T>(codec: { parse(bytes: Uint8Array): T; serialize(value: NoInfer<T>): { toBytes(): Uint8Array } }, data: Uint8Array): T {
  const result = codec.parse(data);
  if (!Buffer.from(codec.serialize(result).toBytes()).equals(Buffer.from(data))) {
    throw new Error('Noncanonical or trailing BCS content');
  }
  return result;
}
