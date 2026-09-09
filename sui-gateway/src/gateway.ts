import { bcs } from '@mysten/sui/bcs';
import { type Signer, parseSerializedSignature } from '@mysten/sui/cryptography';
import { Transaction, TransactionDataBuilder } from '@mysten/sui/transactions';
import { fromBase58, normalizeStructTag, normalizeSuiAddress, toBase58 } from '@mysten/sui/utils';
import { verifyTransactionSignature } from '@mysten/sui/verify';
import { approvalDigest, canonicalIssuerSignature, recoverIssuer } from './approval.js';
import * as C from './codecs.js';
import { base64, bytes, GatewayError, hex, hexBytes, MODULE, operation, parseBatch, parseStatus,
  record, requireValue, uint, ZERO20, ZERO32, type Approval, type Identity, type Operation, type Prepared,
  type PreparedEnvelope } from './contracts.js';
import type { Chain } from './sdk.js';
import { DurableJournal } from './journal.js';

const FUNCTIONS: Record<Operation, string> = {
  ANCHOR_BATCH: 'anchor_batch', REVOKE: 'revoke_credential', SUPERSEDE: 'supersede_credential',
};
const EVENTS: Record<Operation, string> = {
  ANCHOR_BATCH: 'BatchAnchored', REVOKE: 'CredentialRevoked', SUPERSEDE: 'CredentialSuperseded',
};

export function digestToHash(digest: string): string {
  const data = fromBase58(digest);
  requireValue(data.length === 32, 'BLOCKCHAIN_RESPONSE_INVALID', 'Sui digest must contain 32 bytes');
  return hexBytes(data);
}
export function hashToDigest(hash: unknown): string { return toBase58(bytes(hex(hash, 32, 'transactionHash'))); }

/** Durable prepare sidecar. Java also stores Prepared in its outbox BEFORE requesting broadcast. */
export class Gateway {
  private readonly relayerAddress: string;
  constructor(readonly identity: Identity, private readonly chain: Chain, private readonly signer: Signer,
    private readonly journal: DurableJournal,
    private readonly gasBudget = '50000000') {
    requireValue(signer.getKeyScheme() === 'ED25519', 'CONFIG_INVALID', 'Only an Ed25519 Sui relayer is supported');
    this.relayerAddress = hex(signer.toSuiAddress(), 32, 'relayerAddress', true);
  }
  private type(name: string) { return `${this.identity.packageId}::${MODULE}::${name}`; }
  async initialize() {
    await this.journal.initialize();
    await this.chain.verifyPackage(this.identity.packageId);
    await this.registry();
    return this.identity;
  }
  private async registry() {
    requireValue(await this.chain.chainIdentifier() === this.identity.chainIdentifier,
      'BLOCKCHAIN_IDENTITY_MISMATCH', 'RPC chain identifier differs from configured approval domain');
    const object = await this.chain.object(this.identity.registryId);
    requireValue(object.objectId === this.identity.registryId && object.shared && normalizeStructTag(object.type) === this.type('Registry'),
      'BLOCKCHAIN_IDENTITY_MISMATCH', 'Configured registry must be a shared object of the exact configured package');
    let registry;
    try { registry = C.parseBcs(C.Registry, object.content); }
    catch { throw new GatewayError('BLOCKCHAIN_RESPONSE_INVALID', 'Registry BCS does not match the version 1 ABI', false, 502); }
    requireValue(registry.id === this.identity.registryId && registry.package_id === this.identity.packageId &&
      hexBytes(registry.chain_identifier).slice(2) === this.identity.chainIdentifier,
    'BLOCKCHAIN_IDENTITY_MISMATCH', 'Registry content does not match configured package, object, and chain identity');
    return registry;
  }
  private async field<T>(parent: string, keyType: string, key: Uint8Array, valueType: string,
    codec: { parse(bytes: Uint8Array): T; serialize(value: NoInfer<T>): { toBytes(): Uint8Array } }) {
    const field = await this.chain.field(parent, keyType, key);
    if (field === null) return null;
    requireValue(normalizeStructTag(field.type) === this.type(valueType), 'BLOCKCHAIN_RESPONSE_INVALID', 'Dynamic field value type mismatch');
    try { return C.parseBcs(codec, field.bcs); }
    catch { throw new GatewayError('BLOCKCHAIN_RESPONSE_INVALID', 'Dynamic field BCS is malformed', false, 502); }
  }
  private async issuer(issuerId: string, keyVersion: string, registry: Awaited<ReturnType<Gateway['registry']>>) {
    const value = await this.field(registry.issuer_keys.id, this.type('IssuerKeyId'),
      C.IssuerKeyId.serialize({ issuer_id: bytes(issuerId), key_version: keyVersion }).toBytes(), 'IssuerKey', C.IssuerKey);
    if (!value) return { signer: ZERO20, validFrom: '0', validUntil: '0', compromisedAt: '0', exists: false };
    return { signer: hex(hexBytes(value.signer), 20, 'on-chain issuer signer', true), validFrom: value.valid_from,
      validUntil: value.valid_until, compromisedAt: value.compromised_at, exists: true };
  }
  async issuerKey(input: unknown) {
    const v = record(input), issuerId = hex(v.issuerId), keyVersion = uint(v.keyVersion);
    return this.issuer(issuerId, keyVersion, await this.registry());
  }
  async batch(input: unknown) {
    const v = record(input), batchIdHash = hex(v.batchIdHash), registry = await this.registry();
    const value = await this.field(registry.batches.id, 'vector<u8>', bcs.vector(bcs.u8()).serialize(bytes(batchIdHash)).toBytes(), 'Batch', C.Batch);
    if (!value) return { issuerId: ZERO32, merkleRoot: ZERO32, schemaVersionHash: ZERO32, leafCount: '0',
      treeVersion: 0, issuerKeyVersion: '0', anchoredAt: '0', exists: false };
    return { issuerId: hex(hexBytes(value.issuer_id)), merkleRoot: hex(hexBytes(value.merkle_root)),
      schemaVersionHash: hex(hexBytes(value.schema_version_hash)), leafCount: String(value.leaf_count),
      treeVersion: value.tree_version, issuerKeyVersion: value.issuer_key_version, anchoredAt: value.anchored_at, exists: true };
  }
  async status(input: unknown) {
    const v = record(input), issuerId = hex(v.issuerId), credentialIdHash = hex(v.credentialIdHash), registry = await this.registry();
    const value = await this.field(registry.statuses.id, this.type('CredentialKey'),
      C.CredentialKey.serialize({ issuer_id: bytes(issuerId), credential_id_hash: bytes(credentialIdHash) }).toBytes(), 'Status', C.Status);
    if (!value) return { state: 'NONE', effectiveAt: '0', recordedAt: '0', issuerKeyVersion: '0', replacementCredentialIdHash: ZERO32 };
    requireValue(value.state === 1 || value.state === 2, 'BLOCKCHAIN_RESPONSE_INVALID', 'Unknown on-chain credential status');
    return { state: value.state === 1 ? 'REVOKED' : 'SUPERSEDED', effectiveAt: value.effective_at, recordedAt: value.recorded_at,
      issuerKeyVersion: value.issuer_key_version, replacementCredentialIdHash: hex(hexBytes(value.replacement_credential_id_hash)) };
  }
  private writesAllowed() {
    requireValue(this.identity.network === 'testnet' || this.identity.network === 'localnet',
      'BLOCKCHAIN_WRITES_DISABLED', 'Mainnet writes are disabled in this migration gateway');
  }
  async prepareBatch(input: unknown): Promise<Prepared> {
    const v = record(input);
    return this.prepare(parseBatch(v.approval), canonicalIssuerSignature(v.issuerSignature), 'ANCHOR_BATCH');
  }
  async prepareStatus(input: unknown): Promise<Prepared> {
    const v = record(input), approval = parseStatus(v.approval);
    return this.prepare(approval, canonicalIssuerSignature(v.issuerSignature), approval.action === '0' ? 'REVOKE' : 'SUPERSEDE');
  }
  private async prepare(approval: Approval, signature: Uint8Array, op: Operation): Promise<Prepared> {
    this.writesAllowed();
    const registry = await this.registry();
    const tx = new Transaction();
    tx.setSender(this.relayerAddress);
    tx.setGasBudget(this.gasBudget);
    const common = [tx.object(this.identity.registryId), tx.object('0x6'), tx.pure.vector('u8', bytes(approval.issuerId))];
    const args = 'batchIdHash' in approval ? [...common, tx.pure.vector('u8', bytes(approval.batchIdHash)),
      tx.pure.vector('u8', bytes(approval.merkleRoot)), tx.pure.vector('u8', bytes(approval.schemaVersionHash)),
      tx.pure.u32(Number(approval.leafCount)), tx.pure.u16(Number(approval.treeVersion)), tx.pure.u64(approval.issuerKeyVersion),
      tx.pure.u64(approval.approvalNonce), tx.pure.u64(approval.deadline), tx.pure.vector('u8', signature)] :
      [...common, tx.pure.vector('u8', bytes(approval.credentialIdHash)), tx.pure.vector('u8', bytes(approval.replacementCredentialIdHash)),
        tx.pure.u64(approval.effectiveAt), tx.pure.u64(approval.issuerKeyVersion), tx.pure.u64(approval.approvalNonce),
        tx.pure.u64(approval.deadline), tx.pure.vector('u8', signature)];
    tx.moveCall({ target: `${this.identity.packageId}::${MODULE}::${FUNCTIONS[op]}`, arguments: args });
    const expectedPure = tx.getData().inputs.slice(2).map(input => input.Pure!.bytes);
    const validateSavedApproval = (transactionBytes: Uint8Array) => {
      // Digest idempotency is about approval fields, not one particular ECDSA nonce/signature encoding.
      this.validateTransaction(transactionBytes, op, expectedPure.slice(0, -1));
      try {
        const last = Transaction.from(transactionBytes).getData().inputs.at(-1)!.Pure!.bytes;
        const storedSignature = canonicalIssuerSignature(hexBytes(C.parseBcs(bcs.vector(bcs.u8()), base64(last, 'stored issuer signature'))));
        requireValue(recoverIssuer(this.identity, approval, storedSignature) === recoverIssuer(this.identity, approval, signature),
          'BLOCKCHAIN_APPROVAL_INVALID', 'Stored and submitted approval signatures have different issuers');
      } catch { throw new GatewayError('BLOCKCHAIN_APPROVAL_INVALID', 'Persisted issuer approval is inconsistent with this request'); }
    };
    return this.journal.prepare(hexBytes(approvalDigest(this.identity, approval)), this.identity, this.relayerAddress, {
      validateNew: async () => {
        // Emergency revocation/supersession remains available while new issuance is paused (Move ABI).
        requireValue(op !== 'ANCHOR_BATCH' || !registry.paused, 'BLOCKCHAIN_REGISTRY_PAUSED', 'New anchoring is paused');
        const issuer = await this.issuer(approval.issuerId, approval.issuerKeyVersion, registry), now = await this.chain.clockSeconds();
        requireValue(issuer.exists && BigInt(issuer.validFrom) <= now && (issuer.validUntil === '0' || now <= BigInt(issuer.validUntil)) &&
          (issuer.compromisedAt === '0' || now < BigInt(issuer.compromisedAt)),
          'BLOCKCHAIN_ISSUER_INACTIVE', 'Issuer key is missing, retired, not yet valid, or compromised');
        requireValue(BigInt(approval.deadline) >= now, 'BLOCKCHAIN_APPROVAL_EXPIRED', 'Issuer approval has expired');
        if ('effectiveAt' in approval && BigInt(approval.effectiveAt) > now) {
          throw new GatewayError('BLOCKCHAIN_CHAIN_CLOCK_BEHIND',
            'Status effectiveAt is ahead of the on-chain Clock; retry after the chain reaches that timestamp', true, 409);
        }
        let recovered;
        try { recovered = recoverIssuer(this.identity, approval, signature); }
        catch { throw new GatewayError('BLOCKCHAIN_APPROVAL_INVALID', 'Issuer approval signature cannot be recovered'); }
        requireValue(recovered === issuer.signer, 'BLOCKCHAIN_APPROVAL_INVALID', 'Approval is not signed by the registered issuer key');
        const relayer = await this.chain.field(registry.relayers.id, 'address', bcs.Address.serialize(this.relayerAddress).toBytes());
        requireValue(relayer !== null && relayer.type === 'bool' && C.parseBcs(bcs.bool(), relayer.bcs) === true,
          'BLOCKCHAIN_RELAYER_UNAUTHORIZED', 'Sui transaction signer is not an authorized registry relayer');
      },
      build: async () => {
        const transactionBytes = await this.chain.build(tx); // build/simulate only; NEVER execute here
        this.validateTransaction(transactionBytes, op, expectedPure);
        return transactionBytes;
      },
      sign: async transactionBytes => {
        validateSavedApproval(transactionBytes);
        const signed = await this.signer.signTransaction(transactionBytes);
        requireValue(signed.bytes === Buffer.from(transactionBytes).toString('base64'), 'BLOCKCHAIN_RESPONSE_INVALID', 'Signer returned different transaction bytes');
        const transactionDigest = TransactionDataBuilder.getDigestFromBytes(transactionBytes);
        const envelope: PreparedEnvelope = { version: 1, identity: this.identity, operationType: op,
          transactionBytes: signed.bytes, signature: signed.signature, transactionDigest, relayerAddress: this.relayerAddress };
        return { transactionHash: digestToHash(transactionDigest), transactionDigest, transactionNonce: null,
          relayerAddress: this.relayerAddress, signedRawTransaction: Buffer.from(JSON.stringify(envelope)).toString('base64') };
      },
      validatePrepared: async (prepared, transactionBytes) => {
        const verified = await this.verifyPrepared(prepared);
        requireValue(Buffer.from(verified.data).equals(Buffer.from(transactionBytes)) && prepared.transactionNonce === null &&
          prepared.transactionDigest === verified.digest && prepared.relayerAddress === this.relayerAddress,
        'BLOCKCHAIN_PREPARED_INVALID', 'Durable prepared response does not match its signed bytes');
        validateSavedApproval(transactionBytes);
      },
    });
  }
  private validateTransaction(data: Uint8Array, op: Operation, expectedPure?: string[]) {
    try {
      const parsed = TransactionDataBuilder.fromBytes(data);
      requireValue(Buffer.from(parsed.build()).equals(Buffer.from(data)), 'BLOCKCHAIN_PREPARED_INVALID', 'Noncanonical transaction bytes');
      const tx = Transaction.from(data).getData(), cmd = tx.commands[0]?.MoveCall;
      requireValue(tx.sender === this.relayerAddress && tx.gasData.owner === this.relayerAddress &&
        tx.gasData.budget !== null && BigInt(tx.gasData.budget) > 0n && BigInt(tx.gasData.budget) <= BigInt(this.gasBudget),
      'BLOCKCHAIN_PREPARED_INVALID', 'Transaction sender or gas owner/budget differs from configured relayer');
      requireValue(tx.commands.length === 1 && cmd && cmd.package === this.identity.packageId && cmd.module === MODULE &&
        cmd.function === FUNCTIONS[op] && cmd.typeArguments.length === 0,
      'BLOCKCHAIN_PREPARED_INVALID', 'Prepared transaction must contain exactly the expected registry operation');
      requireValue(cmd.arguments.length === (op === 'ANCHOR_BATCH' ? 12 : 10) && tx.inputs.length === cmd.arguments.length &&
        cmd.arguments.every((arg, index) => arg.$kind === 'Input' && arg.Input === index), 'BLOCKCHAIN_PREPARED_INVALID', 'Prepared transaction argument structure is invalid');
      const registry = tx.inputs[0]?.Object?.SharedObject, clock = tx.inputs[1]?.Object?.SharedObject;
      requireValue(registry?.objectId === this.identity.registryId && registry.mutable && clock?.objectId === normalizeSuiAddress('0x6') &&
        !clock.mutable && tx.inputs.slice(2).every(input => input.Pure), 'BLOCKCHAIN_PREPARED_INVALID', 'Prepared transaction object scope is invalid');
      requireValue(!expectedPure || expectedPure.every((value, index) => tx.inputs[index + 2]?.Pure?.bytes === value),
        'BLOCKCHAIN_PREPARED_INVALID', 'RPC transaction resolution changed the issuer-approved argument bytes');
    } catch (error) {
      if (error instanceof GatewayError) throw error;
      throw new GatewayError('BLOCKCHAIN_PREPARED_INVALID', 'Prepared transaction cannot be decoded');
    }
  }
  private async verifyPrepared(input: unknown) {
    const v = record(input), expectedDigest = hashToDigest(v.transactionHash);
    let envelope: Record<string, unknown>;
    try { envelope = record(JSON.parse(Buffer.from(base64(v.signedRawTransaction, 'signedRawTransaction')).toString('utf8'))); }
    catch { throw new GatewayError('BLOCKCHAIN_PREPARED_INVALID', 'Prepared transaction envelope cannot be decoded'); }
    const envelopeIdentity = record(envelope.identity), op = operation(envelope.operationType);
    requireValue(envelope.version === 1 && Object.entries(this.identity).every(([key, value]) => envelopeIdentity[key] === value) &&
      envelope.relayerAddress === this.relayerAddress, 'BLOCKCHAIN_PREPARED_INVALID', 'Prepared envelope identity mismatch');
    const data = base64(envelope.transactionBytes, 'transactionBytes'), digest = TransactionDataBuilder.getDigestFromBytes(data);
    requireValue(digest === expectedDigest && envelope.transactionDigest === digest, 'BLOCKCHAIN_PREPARED_HASH_MISMATCH', 'Persisted transaction digest does not match its bytes');
    this.validateTransaction(data, op);
    try {
      requireValue(typeof envelope.signature === 'string' && parseSerializedSignature(envelope.signature).signatureScheme === 'ED25519',
        'BLOCKCHAIN_PREPARED_INVALID', 'Prepared envelope requires a Sui Ed25519 signature');
      await verifyTransactionSignature(data, envelope.signature, { address: this.relayerAddress });
    } catch { throw new GatewayError('BLOCKCHAIN_PREPARED_INVALID', 'Persisted Sui signature is invalid for these transaction bytes'); }
    return { data, digest, signature: envelope.signature as string };
  }
  async broadcast(input: unknown) {
    this.writesAllowed();
    const { data, digest, signature } = await this.verifyPrepared(input);
    await this.registry(); // No broadcast to an endpoint whose network or registry identity changed.
    try {
      const result = await this.chain.execute(data, signature); // Sole broadcast boundary.
      requireValue(result.digest === digest, 'BLOCKCHAIN_BROADCAST_AMBIGUOUS', 'RPC returned a different digest');
      requireValue(result.success, 'BLOCKCHAIN_BROADCAST_REJECTED', 'Sui execution failed; reconcile the persisted transaction receipt');
      return { transactionHash: digestToHash(digest), transactionDigest: digest, accepted: true };
    } catch (error) {
      if (error instanceof GatewayError && error.code === 'BLOCKCHAIN_BROADCAST_REJECTED') throw error;
      throw new GatewayError('BLOCKCHAIN_BROADCAST_AMBIGUOUS', 'Broadcast outcome is unknown; reconcile this digest and retry only the exact persisted bytes', true, 502);
    }
  }
  async receipt(input: unknown) {
    const v = record(input), digest = hashToDigest(v.transactionHash), op = operation(v.operationType);
    await this.registry();
    const pending = { state: 'PENDING', blockNumber: '0', blockHash: null, eventLogIndex: -1, revertReason: null,
      transactionDigest: digest, checkpointDigest: null };
    const tx = await this.chain.transaction(digest);
    if (tx === null) return pending;
    requireValue(tx.digest === digest, 'BLOCKCHAIN_RESPONSE_INVALID', 'Receipt transaction digest mismatch');
    if (tx.checkpoint === null) return pending;
    const sequence = uint(tx.checkpoint), checkpoint = await this.chain.checkpoint(sequence);
    requireValue(checkpoint.sequence === sequence, 'BLOCKCHAIN_RESPONSE_INVALID', 'Checkpoint sequence mismatch');
    const common = { blockNumber: sequence, blockHash: digestToHash(checkpoint.digest), transactionDigest: digest, checkpointDigest: checkpoint.digest };
    if (!tx.success) return { ...common, state: 'REVERTED', eventLogIndex: -1, revertReason: 'Sui transaction execution failed' };
    const matches: number[] = [];
    for (const [index, event] of tx.events.entries()) {
      if (event.packageId !== this.identity.packageId || event.module !== MODULE || normalizeStructTag(event.eventType) !== this.type(EVENTS[op])) continue;
      let decoded: { registry_id: string };
      try {
        decoded = op === 'ANCHOR_BATCH' ? C.parseBcs(C.BatchAnchored, event.bcs) : op === 'REVOKE' ?
          C.parseBcs(C.CredentialRevoked, event.bcs) : C.parseBcs(C.CredentialSuperseded, event.bcs);
      } catch { throw new GatewayError('BLOCKCHAIN_RECEIPT_EVIDENCE_MISSING', 'Registry event BCS is malformed', false, 502); }
      if (decoded.registry_id === this.identity.registryId) matches.push(index);
    }
    requireValue(matches.length === 1, 'BLOCKCHAIN_RECEIPT_EVIDENCE_MISSING', 'Successful receipt lacks exactly one expected package/module/registry operation event');
    return { ...common, state: 'CONFIRMED', eventLogIndex: matches[0], revertReason: null };
  }
}
