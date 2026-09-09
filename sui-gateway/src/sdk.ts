import { SuiGrpcClient } from '@mysten/sui/grpc';
import { ObjectError, TransactionError } from '@mysten/sui/client';
import { Transaction } from '@mysten/sui/transactions';
import { fromBase58, normalizeStructTag, normalizeSuiAddress } from '@mysten/sui/utils';
import { GatewayError, hexBytes, MODULE, requireValue } from './contracts.js';
import type { Config } from './config.js';
import { Clock, parseBcs } from './codecs.js';

export type ChainEvent = { packageId: string; module: string; eventType: string; bcs: Uint8Array };
export type ChainTransaction = { digest: string; success: boolean; checkpoint: string | null; events: ChainEvent[] };
export interface Chain {
  chainIdentifier(): Promise<string>;
  clockSeconds(): Promise<bigint>;
  verifyPackage(packageId: string): Promise<void>;
  object(objectId: string): Promise<{ objectId: string; type: string; shared: boolean; content: Uint8Array }>;
  field(parentId: string, type: string, name: Uint8Array): Promise<{ type: string; bcs: Uint8Array } | null>;
  build(transaction: Transaction): Promise<Uint8Array>;
  execute(transaction: Uint8Array, signature: string): Promise<{ digest: string; success: boolean }>;
  transaction(digest: string): Promise<ChainTransaction | null>;
  checkpoint(sequence: string): Promise<{ sequence: string; digest: string }>;
}

/** Only this adapter knows SDK transport shapes. SDK 2.29 is locked in package-lock.json. */
export class GrpcChain implements Chain {
  readonly client: SuiGrpcClient;
  constructor(private readonly config: Pick<Config, 'identity' | 'rpcUrl' | 'timeoutMs'>, client?: SuiGrpcClient) {
    this.client = client ?? new SuiGrpcClient({ network: config.identity.network,
      baseUrl: config.rpcUrl, timeout: config.timeoutMs });
  }
  private signal() { return AbortSignal.timeout(this.config.timeoutMs); }
  async chainIdentifier() {
    // getChainIdentifier() caches service info. Query fresh at each identity gate.
    const { response } = await this.client.ledgerService.getServiceInfo({}, { abort: this.signal() });
    requireValue(response.chainId, 'BLOCKCHAIN_IDENTITY_MISMATCH', 'RPC did not report a genesis digest');
    requireValue(this.config.identity.network === 'localnet' ? ['localnet', 'unknown'].includes(response.chain ?? '') :
      response.chain === this.config.identity.network,
    'BLOCKCHAIN_IDENTITY_MISMATCH', 'RPC network name differs from configured network');
    const genesis = fromBase58(response.chainId);
    requireValue(genesis.length === 32, 'BLOCKCHAIN_IDENTITY_MISMATCH', 'RPC genesis digest is malformed');
    return hexBytes(genesis.slice(0, 4)).slice(2);
  }
  async verifyPackage(packageId: string) {
    await Promise.all(['anchor_batch', 'revoke_credential', 'supersede_credential'].map(name =>
      this.client.getMoveFunction({ packageId, moduleName: MODULE, name, signal: this.signal() })));
  }
  async clockSeconds() {
    const clockId = normalizeSuiAddress('0x6'), object = await this.object(clockId);
    requireValue(object.objectId === clockId && object.shared && normalizeStructTag(object.type) === normalizeStructTag('0x2::clock::Clock'),
      'BLOCKCHAIN_RESPONSE_INVALID', 'RPC Clock must be the canonical shared Sui Clock object');
    let clock;
    try { clock = parseBcs(Clock, object.content); }
    catch { throw new GatewayError('BLOCKCHAIN_RESPONSE_INVALID', 'Sui Clock BCS is malformed', false, 502); }
    requireValue(clock.id === clockId, 'BLOCKCHAIN_RESPONSE_INVALID', 'Sui Clock UID is inconsistent');
    return BigInt(clock.timestamp_ms) / 1000n;
  }
  async object(objectId: string) {
    const { object } = await this.client.getObject({ objectId, include: { content: true }, signal: this.signal() });
    return { objectId: object.objectId, type: object.type, shared: object.owner.$kind === 'Shared', content: object.content };
  }
  async field(parentId: string, type: string, name: Uint8Array) {
    try {
      const { dynamicField } = await this.client.getDynamicField({ parentId, name: { type, bcs: name }, signal: this.signal() });
      return dynamicField.value;
    } catch (error) {
      // Never translate unavailable, malformed, deleted, or permission failures to absence.
      if (error instanceof ObjectError && error.reason === 'notFound') return null;
      throw error;
    }
  }
  async build(transaction: Transaction) { return transaction.build({ client: this.client }); }
  async execute(transaction: Uint8Array, signature: string) {
    const result = await this.client.executeTransaction({ transaction, signatures: [signature], signal: this.signal() });
    const tx = result.Transaction ?? result.FailedTransaction;
    return { digest: tx.digest, success: tx.status.success };
  }
  async transaction(digest: string) {
    try {
      const result = await this.client.getTransaction({ digest, include: { events: true, effects: true }, signal: this.signal() });
      const tx = result.Transaction ?? result.FailedTransaction;
      requireValue(tx.status.success === tx.effects.status.success, 'BLOCKCHAIN_RESPONSE_INVALID', 'Transaction and effects status disagree');
      return { digest: tx.digest, success: tx.status.success, checkpoint: tx.checkpoint, events: tx.events };
    } catch (error) {
      if (error instanceof TransactionError && error.reason === 'notFound') return null;
      throw error;
    }
  }
  async checkpoint(sequence: string) {
    const { response } = await this.client.ledgerService.getCheckpoint({
      checkpointId: { oneofKind: 'sequenceNumber', sequenceNumber: BigInt(sequence) },
      readMask: { paths: ['sequence_number', 'digest'] },
    }, { abort: this.signal() });
    if (response.checkpoint?.sequenceNumber === undefined || !response.checkpoint.digest) {
      throw new GatewayError('BLOCKCHAIN_RESPONSE_INVALID', 'RPC checkpoint evidence is missing', true, 502);
    }
    return { sequence: response.checkpoint.sequenceNumber.toString(), digest: response.checkpoint.digest };
  }
}
