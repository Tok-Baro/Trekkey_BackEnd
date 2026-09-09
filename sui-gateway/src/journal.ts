import { constants } from 'node:fs';
import { open, mkdir, lstat, rename } from 'node:fs/promises';
import { isAbsolute, join } from 'node:path';
import { randomUUID } from 'node:crypto';
import { TransactionDataBuilder } from '@mysten/sui/transactions';
import { base64, GatewayError, hex, record, requireValue, uint, type Identity, type Prepared } from './contracts.js';

type GasRef = { objectId: string; version: string };
type JournalEntry = {
  version: 1; approvalDigest: string; identity: Identity; relayerAddress: string;
  stage: 'BUILDING' | 'UNSIGNED' | 'SIGNED'; transactionBytes?: string; transactionDigest?: string;
  gasRefs?: GasRef[]; prepared?: Prepared;
};
type FaultPoint = 'afterBuild' | 'afterUnsignedPersist' | 'afterReservations' | 'beforeSignedPersist';
const journalFailure = () => new GatewayError('BLOCKCHAIN_JOURNAL_RECOVERY_REQUIRED',
  'Durable prepare state is incomplete or inconsistent; preserve the journal and reconcile manually before retrying', false, 409);
const isMissing = (error: unknown) => error instanceof Error && 'code' in error && error.code === 'ENOENT';
const isExists = (error: unknown) => error instanceof Error && 'code' in error && error.code === 'EEXIST';

/**
 * This directory is durable custody of signed transactions, not a disposable cache.
 * Atomic wx claims coordinate processes. No TTL/unlink of gas reservations is performed.
 */
export class DurableJournal {
  private queue: Promise<void> = Promise.resolve();
  constructor(readonly directory: string, private readonly fault?: (point: FaultPoint) => void) {
    requireValue(isAbsolute(directory) && directory !== '/', 'CONFIG_INVALID', 'A dedicated absolute journal directory is required');
  }
  private async exclusive<T>(work: () => Promise<T>): Promise<T> {
    const previous = this.queue;
    let release!: () => void;
    this.queue = new Promise<void>(resolve => { release = resolve; });
    await previous;
    try { return await work(); } finally { release(); }
  }
  private async syncDirectory() {
    const file = await open(this.directory, constants.O_RDONLY | constants.O_NOFOLLOW);
    try { await file.sync(); } finally { await file.close(); }
  }
  async initialize() {
    await mkdir(this.directory, { recursive: true, mode: 0o700 });
    const stat = await lstat(this.directory);
    requireValue(stat.isDirectory() && !stat.isSymbolicLink() && (stat.mode & 0o077) === 0 &&
      (process.getuid === undefined || stat.uid === process.getuid()), 'CONFIG_INVALID', 'Journal must be an owner-only directory (0700), not a symlink');
  }
  private async read(path: string): Promise<unknown | null> {
    let file;
    try { file = await open(path, constants.O_RDONLY | constants.O_NOFOLLOW); }
    catch (error) { if (isMissing(error)) return null; throw journalFailure(); }
    try {
      const stat = await file.stat();
      if (!stat.isFile() || stat.size > 256_000 || (stat.mode & 0o077) !== 0 || stat.nlink !== 1 ||
        (process.getuid !== undefined && stat.uid !== process.getuid())) throw journalFailure();
      return JSON.parse(await file.readFile('utf8'));
    } catch { throw journalFailure(); } finally { await file.close(); }
  }
  private async create(path: string, value: unknown): Promise<boolean> {
    let file;
    try { file = await open(path, constants.O_WRONLY | constants.O_CREAT | constants.O_EXCL | constants.O_NOFOLLOW, 0o600); }
    catch (error) { if (isExists(error)) return false; throw journalFailure(); }
    try { await file.writeFile(JSON.stringify(value)); await file.sync(); }
    catch { throw journalFailure(); } finally { await file.close(); }
    await this.syncDirectory();
    return true;
  }
  private async replace(path: string, value: unknown) {
    const temp = `${path}.${randomUUID()}.tmp`;
    await this.create(temp, value);
    await rename(temp, path);
    await this.syncDirectory(); // Never return a signed envelope before durable rename.
  }
  private validate(value: unknown, key: string, identity: Identity, relayerAddress: string): JournalEntry {
    try {
      const v = record(value), id = record(v.identity);
      requireValue(v.version === 1 && v.approvalDigest === key && v.relayerAddress === relayerAddress &&
        Object.entries(identity).every(([name, data]) => id[name] === data) &&
        ['BUILDING', 'UNSIGNED', 'SIGNED'].includes(String(v.stage)), 'JOURNAL_INVALID', 'Journal identity mismatch');
      const entry = v as unknown as JournalEntry;
      if (entry.stage !== 'BUILDING') {
        const data = base64(entry.transactionBytes, 'journal transaction bytes');
        requireValue(TransactionDataBuilder.getDigestFromBytes(data) === entry.transactionDigest && Array.isArray(entry.gasRefs),
          'JOURNAL_INVALID', 'Journal bytes or gas references mismatch');
        const refs = TransactionDataBuilder.fromBytes(data).gasData.payment;
        requireValue(refs !== null && refs.length > 0 && JSON.stringify(refs.map(r => ({ objectId: r.objectId, version: String(r.version) }))) === JSON.stringify(entry.gasRefs),
          'JOURNAL_INVALID', 'Journal gas references differ from its transaction');
      }
      if (entry.stage === 'SIGNED') requireValue(entry.prepared && typeof entry.prepared.signedRawTransaction === 'string', 'JOURNAL_INVALID', 'Signed result missing');
      return entry;
    } catch { throw journalFailure(); }
  }
  private async reserve(entry: JournalEntry) {
    for (const ref of [...entry.gasRefs!].sort((a, b) => a.objectId.localeCompare(b.objectId))) {
      const objectId = hex(ref.objectId), version = uint(ref.version);
      const path = join(this.directory, `gas-${objectId.slice(2)}-${version}.json`);
      const reservation = { version: 1, objectId, objectVersion: version,
        approvalDigest: entry.approvalDigest, transactionDigest: entry.transactionDigest };
      if (!await this.create(path, reservation)) {
        const existing = await this.read(path);
        if (!existing || JSON.stringify(existing) !== JSON.stringify(reservation)) {
          throw new GatewayError('BLOCKCHAIN_GAS_RESERVED', 'A different approval already reserved this gas object version; reconcile the existing transaction first', false, 409);
        }
        // An identical concurrent reservation may have been read just before its writer fsync.
        const file = await open(path, constants.O_RDONLY | constants.O_NOFOLLOW);
        try { await file.sync(); } finally { await file.close(); }
        await this.syncDirectory();
      }
    }
  }
  async prepare(keyValue: string, identity: Identity, relayerAddress: string, callbacks: {
    validateNew(): Promise<void>; build(): Promise<Uint8Array>; sign(data: Uint8Array): Promise<Prepared>;
    validatePrepared(prepared: Prepared, data: Uint8Array): Promise<void>;
  }): Promise<Prepared> {
    return this.exclusive(async () => {
      await this.initialize();
      const key = hex(keyValue), path = join(this.directory, `approval-${key.slice(2)}.json`);
      let existing = await this.read(path), ownClaim = false;
      if (existing === null) {
        await callbacks.validateNew(); // Invalid issuer approvals cannot poison a durable digest claim.
        const claim: JournalEntry = { version: 1, approvalDigest: key, identity, relayerAddress, stage: 'BUILDING' };
        ownClaim = await this.create(path, claim);
        existing = ownClaim ? claim : await this.read(path);
      }
      let entry = this.validate(existing, key, identity, relayerAddress);
      if (entry.stage === 'BUILDING') {
        if (!ownClaim) throw journalFailure(); // Crash before durable bytes: never silently rebuild a new transaction.
        const data = await callbacks.build();
        this.fault?.('afterBuild');
        const payment = TransactionDataBuilder.fromBytes(data).gasData.payment;
        requireValue(payment !== null && payment.length > 0, 'BLOCKCHAIN_GAS_UNSUPPORTED', 'Durable journal currently requires explicit gas coin references');
        entry = { ...entry, stage: 'UNSIGNED', transactionBytes: Buffer.from(data).toString('base64'),
          transactionDigest: TransactionDataBuilder.getDigestFromBytes(data), gasRefs: payment.map(r => ({ objectId: r.objectId, version: String(r.version) })) };
        await this.replace(path, entry);
        this.fault?.('afterUnsignedPersist');
      }
      const data = base64(entry.transactionBytes, 'journal transaction bytes');
      await this.reserve(entry);
      if (entry.stage === 'SIGNED') {
        await callbacks.validatePrepared(entry.prepared!, data);
        return entry.prepared!;
      }
      // Every selected gas version is durable and exclusive BEFORE any signature.
      this.fault?.('afterReservations');
      const prepared = await callbacks.sign(data);
      await callbacks.validatePrepared(prepared, data);
      this.fault?.('beforeSignedPersist');
      await this.replace(path, { ...entry, stage: 'SIGNED', prepared });
      return prepared;
    });
  }
}
