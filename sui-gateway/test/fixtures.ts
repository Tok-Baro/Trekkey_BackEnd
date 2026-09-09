import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { after } from 'node:test';
import { bcs } from '@mysten/sui/bcs';
import { Ed25519Keypair } from '@mysten/sui/keypairs/ed25519';
import { Inputs, Transaction, TransactionDataBuilder } from '@mysten/sui/transactions';
import { toBase58 } from '@mysten/sui/utils';
import * as C from '../src/codecs.js';
import { bytes, MODULE, type Identity } from '../src/contracts.js';
import { Gateway } from '../src/gateway.js';
import type { Chain, ChainTransaction } from '../src/sdk.js';
import { DurableJournal } from '../src/journal.js';

export const fixture = JSON.parse(readFileSync(new URL('../../contracts-sui/test-fixtures/approval-v1.json', import.meta.url), 'utf8'));
export const identity: Identity = { ...fixture.domain, network: 'testnet', protocolVersion: 1 };
export const h = (n: number) => `0x${n.toString(16).padStart(2, '0').repeat(32)}`;
export const testSigner = Ed25519Keypair.fromSecretKey(new Uint8Array(32).fill(7)); // Synthetic, public test seed only.
export const registry = () => ({ id: identity.registryId, chain_identifier: bytes(identity.chainIdentifier),
  package_id: identity.packageId, paused: false, issuer_keys: { id: h(90), size: '1' }, batches: { id: h(91), size: '0' },
  statuses: { id: h(92), size: '0' }, used_nonces: { id: h(93), size: '0' }, used_digests: { id: h(94), size: '0' },
  relayers: { id: h(95), size: '1' } });
export const issuer = () => ({ signer: bytes(fixture.signer), valid_from: '1700000000', valid_until: '0', compromised_at: '0' });
export function event(registryId = identity.registryId) {
  const a = fixture.batch.approval;
  return { packageId: identity.packageId, module: MODULE, eventType: `${identity.packageId}::${MODULE}::BatchAnchored`,
    bcs: C.BatchAnchored.serialize({ registry_id: registryId, issuer_id: bytes(a.issuerId), batch_id_hash: bytes(a.batchIdHash),
      merkle_root: bytes(a.merkleRoot), schema_version_hash: bytes(a.schemaVersionHash), leaf_count: 3, tree_version: 1,
      issuer_key_version: '2', anchored_at: '1800000000' }).toBytes() };
}
export class FakeChain implements Chain {
  chainId = identity.chainIdentifier;
  clock = 1800000000n;
  registry = registry();
  issuer = issuer();
  issuerExists = true;
  relayerAllowed = true;
  buildCount = 0;
  executeCount = 0;
  submitted: Uint8Array[] = [];
  tx: ChainTransaction | null = null;
  fieldFailure: Error | null = null;
  executeFailure: Error | null = null;
  execSuccess = true;
  gasVersion = '1';
  checkpointDigest = toBase58(bytes(h(99)));
  checkpointSequence = '25';
  async chainIdentifier() { return this.chainId; }
  async clockSeconds() { return this.clock; }
  async verifyPackage() {}
  async object(objectId: string) { return { objectId, type: `${identity.packageId}::${MODULE}::Registry`, shared: true,
    content: C.Registry.serialize(this.registry).toBytes() }; }
  async field(parentId: string, _type: string, _name: Uint8Array) {
    if (this.fieldFailure) throw this.fieldFailure;
    if (parentId === this.registry.issuer_keys.id) return this.issuerExists ?
      { type: `${identity.packageId}::${MODULE}::IssuerKey`, bcs: C.IssuerKey.serialize(this.issuer).toBytes() } : null;
    if (parentId === this.registry.relayers.id) return this.relayerAllowed ? { type: 'bool', bcs: bcs.bool().serialize(true).toBytes() } : null;
    return null;
  }
  async build(tx: Transaction) {
    this.buildCount++;
    const data = TransactionDataBuilder.restore(tx.getData());
    data.inputs = data.inputs.map(input => input.UnresolvedObject ? Inputs.SharedObjectRef({
      objectId: input.UnresolvedObject.objectId, initialSharedVersion: '1',
      mutable: input.UnresolvedObject.objectId === identity.registryId,
    }) : input);
    data.gasData.owner = testSigner.toSuiAddress();
    data.gasData.price = '1000';
    data.gasData.payment = [{ objectId: h(96), version: this.gasVersion, digest: toBase58(bytes(h(97))) }];
    return data.build();
  }
  async execute(data: Uint8Array, _signature: string) {
    this.executeCount++;
    this.submitted.push(data);
    if (this.executeFailure) throw this.executeFailure;
    return { digest: TransactionDataBuilder.getDigestFromBytes(data), success: this.execSuccess };
  }
  async transaction(_digest: string) { return this.tx; }
  async checkpoint(_sequence: string) { return { sequence: this.checkpointSequence, digest: this.checkpointDigest }; }
}
const testDirectories: string[] = [];
export function journalDirectory() {
  const dir = mkdtempSync(join(tmpdir(), 'trekkey-sui-journal-test-'));
  testDirectories.push(dir);
  return dir;
}
after(() => { for (const dir of testDirectories) rmSync(dir, { recursive: true, force: true }); });
export function setup(override: Partial<Identity> = {}, journal = new DurableJournal(journalDirectory())) {
  const chain = new FakeChain();
  return { chain, journal, gateway: new Gateway({ ...identity, ...override }, chain, testSigner, journal, '50000000') };
}
