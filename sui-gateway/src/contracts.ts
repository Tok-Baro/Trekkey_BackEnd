export class GatewayError extends Error {
  constructor(public readonly code: string, message: string, public readonly retryable = false,
    public readonly status = 400) { super(message); }
}

export function requireValue(condition: unknown, code: string, message: string): asserts condition {
  if (!condition) throw new GatewayError(code, message);
}

export const ZERO32 = `0x${'00'.repeat(32)}`;
export const ZERO20 = `0x${'00'.repeat(20)}`;
export const MODULE = 'credential_registry';
export const SCHEME = 'TREKKEY_SUI_APPROVAL_V1';
export type Operation = 'ANCHOR_BATCH' | 'REVOKE' | 'SUPERSEDE';
export type Identity = {
  network: 'testnet' | 'localnet' | 'mainnet'; chainIdentifier: string;
  packageId: string; registryId: string; protocolVersion: 1;
};
export type BatchApproval = {
  issuerId: string; batchIdHash: string; merkleRoot: string; schemaVersionHash: string;
  leafCount: string; treeVersion: string; issuerKeyVersion: string; approvalNonce: string; deadline: string;
};
export type StatusApproval = {
  issuerId: string; credentialIdHash: string; action: '0' | '1'; replacementCredentialIdHash: string;
  effectiveAt: string; issuerKeyVersion: string; approvalNonce: string; deadline: string;
};
export type Approval = BatchApproval | StatusApproval;
export type Prepared = {
  transactionHash: string; transactionDigest: string; transactionNonce: null;
  relayerAddress: string; signedRawTransaction: string;
};
export type PreparedEnvelope = {
  version: 1; identity: Identity; operationType: Operation;
  transactionBytes: string; signature: string; transactionDigest: string; relayerAddress: string;
};

export function record(value: unknown): Record<string, unknown> {
  requireValue(value !== null && typeof value === 'object' && !Array.isArray(value), 'INVALID_INPUT', 'JSON object required');
  return value as Record<string, unknown>;
}
export function hex(value: unknown, length = 32, label = 'hash', nonzero = false): string {
  requireValue(typeof value === 'string' && new RegExp(`^0x[0-9a-fA-F]{${length * 2}}$`).test(value),
    'INVALID_INPUT', `${label} must be 0x-prefixed ${length}-byte hexadecimal`);
  const result = value.toLowerCase();
  requireValue(!nonzero || result !== `0x${'00'.repeat(length)}`, 'INVALID_INPUT', `${label} must be nonzero`);
  return result;
}
export function bytes(value: string): Uint8Array { return Uint8Array.from(Buffer.from(value.replace(/^0x/, ''), 'hex')); }
export function hexBytes(value: Uint8Array | number[]): string { return `0x${Buffer.from(value).toString('hex')}`; }
export function uint(value: unknown, bits = 64, label = 'integer'): string {
  // Java JSON serializes some small inputs as numbers. Never accept imprecise JSON numbers.
  if (typeof value === 'number') {
    requireValue(Number.isSafeInteger(value), 'INVALID_INPUT', `${label} must be an exact integer`);
    value = String(value);
  }
  requireValue(typeof value === 'string' && /^(0|[1-9][0-9]*)$/.test(value), 'INVALID_INPUT', `${label} must be an unsigned decimal integer`);
  requireValue(BigInt(value) < 1n << BigInt(bits), 'INVALID_INPUT', `${label} exceeds uint${bits}`);
  return value;
}
export function operation(value: unknown): Operation {
  requireValue(value === 'ANCHOR_BATCH' || value === 'REVOKE' || value === 'SUPERSEDE',
    'BLOCKCHAIN_OPERATION_UNSUPPORTED', 'Unsupported credential operation');
  return value;
}
export function base64(value: unknown, label: string, maxBytes = 256_000): Uint8Array {
  requireValue(typeof value === 'string' && value.length <= Math.ceil(maxBytes / 3) * 4 &&
    /^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(value),
  'INVALID_INPUT', `${label} must be canonical base64`);
  const decoded = Uint8Array.from(Buffer.from(value, 'base64'));
  requireValue(decoded.length > 0 && decoded.length <= maxBytes && Buffer.from(decoded).toString('base64') === value,
    'INVALID_INPUT', `${label} must be nonempty canonical base64`);
  return decoded;
}

export function parseBatch(value: unknown): BatchApproval {
  const v = record(value);
  const result = {
    issuerId: hex(v.issuerId, 32, 'issuerId', true), batchIdHash: hex(v.batchIdHash, 32, 'batchIdHash', true), merkleRoot: hex(v.merkleRoot, 32, 'merkleRoot', true),
    schemaVersionHash: hex(v.schemaVersionHash, 32, 'schemaVersionHash', true), leafCount: uint(v.leafCount, 32, 'leafCount'),
    treeVersion: uint(v.treeVersion, 16, 'treeVersion'), issuerKeyVersion: uint(v.issuerKeyVersion),
    approvalNonce: uint(v.approvalNonce), deadline: uint(v.deadline),
  };
  requireValue(result.leafCount !== '0' && result.treeVersion === '1' && result.issuerKeyVersion !== '0', 'INVALID_INPUT', 'Positive leafCount/keyVersion and treeVersion=1 required');
  return result;
}
export function parseStatus(value: unknown): StatusApproval {
  const v = record(value);
  const action = v.action === 'REVOKE' ? '0' : v.action === 'SUPERSEDE' ? '1' : uint(v.action, 8, 'action');
  requireValue(action === '0' || action === '1', 'INVALID_INPUT', 'action must be 0 (REVOKE) or 1 (SUPERSEDE)');
  const result = {
    issuerId: hex(v.issuerId, 32, 'issuerId', true), credentialIdHash: hex(v.credentialIdHash, 32, 'credentialIdHash', true), action,
    replacementCredentialIdHash: hex(v.replacementCredentialIdHash), effectiveAt: uint(v.effectiveAt),
    issuerKeyVersion: uint(v.issuerKeyVersion), approvalNonce: uint(v.approvalNonce), deadline: uint(v.deadline),
  };
  requireValue(result.effectiveAt !== '0' && result.issuerKeyVersion !== '0', 'INVALID_INPUT', 'Positive effectiveAt and issuerKeyVersion required');
  requireValue(action === '0' ? result.replacementCredentialIdHash === ZERO32 :
    result.replacementCredentialIdHash !== ZERO32 && result.replacementCredentialIdHash !== result.credentialIdHash,
  'INVALID_INPUT', 'Invalid replacement credential for the requested action');
  return result as StatusApproval;
}
