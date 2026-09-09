import { keccak_256 } from '@noble/hashes/sha3.js';
import { secp256k1 } from '@noble/curves/secp256k1.js';
import { bytes, hex, hexBytes, requireValue, SCHEME, type Approval, type Identity } from './contracts.js';

export const BATCH_TYPE = 'BatchApproval(bytes32 issuerId,bytes32 batchIdHash,bytes32 merkleRoot,bytes32 schemaVersionHash,uint32 leafCount,uint16 treeVersion,uint64 issuerKeyVersion,uint64 approvalNonce,uint64 deadline)';
export const STATUS_TYPE = 'StatusApproval(bytes32 issuerId,bytes32 credentialIdHash,uint8 action,bytes32 replacementCredentialIdHash,uint64 effectiveAt,uint64 issuerKeyVersion,uint64 approvalNonce,uint64 deadline)';
const hashText = (value: string) => keccak_256(new TextEncoder().encode(value));
const join = (...values: Uint8Array[]) => Uint8Array.from(Buffer.concat(values));
const word = (value: string) => bytes(BigInt(value).toString(16).padStart(64, '0'));

export function approvalStructHash(a: Approval): Uint8Array {
  return keccak_256('batchIdHash' in a ? join(hashText(BATCH_TYPE), bytes(a.issuerId), bytes(a.batchIdHash),
    bytes(a.merkleRoot), bytes(a.schemaVersionHash), word(a.leafCount), word(a.treeVersion), word(a.issuerKeyVersion),
    word(a.approvalNonce), word(a.deadline)) : join(hashText(STATUS_TYPE), bytes(a.issuerId), bytes(a.credentialIdHash),
    word(a.action), bytes(a.replacementCredentialIdHash), word(a.effectiveAt), word(a.issuerKeyVersion), word(a.approvalNonce), word(a.deadline)));
}
export function approvalDigest(identity: Pick<Identity, 'chainIdentifier' | 'packageId' | 'registryId'>, approval: Approval): Uint8Array {
  requireValue(/^[0-9a-f]{8}$/.test(identity.chainIdentifier), 'INVALID_INPUT', 'chainIdentifier must be four lowercase hex bytes');
  const domain = keccak_256(join(new TextEncoder().encode(SCHEME), bytes(identity.chainIdentifier),
    bytes(hex(identity.packageId, 32, 'packageId', true)), bytes(hex(identity.registryId, 32, 'registryId', true))));
  return keccak_256(join(bytes('1901'), domain, approvalStructHash(approval)));
}

// This issuer signature is Ethereum-compatible r||s||v, NOT a Sui transaction signature.
export function canonicalIssuerSignature(value: unknown): Uint8Array {
  const sig = bytes(hex(value, 65, 'issuerSignature'));
  if (sig[64] === 0 || sig[64] === 1) sig[64] += 27;
  requireValue(sig[64] === 27 || sig[64] === 28, 'INVALID_INPUT', 'Issuer recovery byte must be 0, 1, 27, or 28');
  const r = BigInt(hexBytes(sig.slice(0, 32))), s = BigInt(hexBytes(sig.slice(32, 64)));
  requireValue(r > 0n && r < secp256k1.Point.Fn.ORDER && s > 0n && s <= secp256k1.Point.Fn.ORDER / 2n,
    'INVALID_INPUT', 'Issuer signature must use valid r and canonical low-s');
  return sig;
}
export function recoverIssuer(identity: Identity, approval: Approval, signature: Uint8Array): string {
  const recovered = join(Uint8Array.of(signature[64]! - 27), signature.slice(0, 64));
  const pubkey = secp256k1.recoverPublicKey(recovered, approvalDigest(identity, approval), { prehash: false });
  const uncompressed = secp256k1.Point.fromBytes(pubkey).toBytes(false);
  return hexBytes(keccak_256(uncompressed.slice(1)).slice(12));
}
export function signIssuer(identity: Identity, approval: Approval, secretKey: Uint8Array): string {
  const sig = secp256k1.sign(approvalDigest(identity, approval), secretKey, { prehash: false, lowS: true, format: 'recovered' });
  requireValue(sig[0] === 0 || sig[0] === 1, 'INVALID_SIGNATURE', 'Unexpected recovery id; retry with a fresh approval nonce');
  return hexBytes(join(sig.slice(1), Uint8Array.of(sig[0]! + 27)));
}
