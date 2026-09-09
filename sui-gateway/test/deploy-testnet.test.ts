import assert from 'node:assert/strict';
import test from 'node:test';
import { createHash } from 'node:crypto';
import { chmod, mkdtemp, readFile, realpath, rm, stat, symlink, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { Transaction, TransactionDataBuilder } from '@mysten/sui/transactions';
import { toBase58 } from '@mysten/sui/utils';
import { PrivateState, GAS_BUDGET, MAX_TOTAL_GAS_BUDGET, parseBytecode, savedPhase, syntheticIssuerId,
  validateEndpoint, verifyTestnet, UpgradeCapBcs, validateInitialUpgradeCap, safeDeploymentFailure, type PhaseTransport } from '../src/deploy-testnet.js';
import { FakeChain, h, identity, testSigner } from './fixtures.js';

// Synthetic public seed only. No initKeys(), network client, faucet, deployment CLI or remote execution is used here.
async function fixture(t: { after(fn: () => Promise<void>): void }) {
  const path = await mkdtemp(join(await realpath(tmpdir()), 'trekkey-deploy-offline-'));
  t.after(() => rm(path, { recursive: true, force: true }));
  const state = new PrivateState(path); await state.initialize(); return state;
}
function transaction() {
  const tx = new Transaction();
  tx.moveCall({ target: `${identity.packageId}::credential_registry::create_registry`,
    arguments: [tx.pure.vector('u8', [1, 2, 3, 4])] });
  return tx;
}
function transport(state: PrivateState, failure = false) {
  let stored: Awaited<ReturnType<PhaseTransport['lookup']>> = null;
  let executions = 0, gates = 0;
  const chain: PhaseTransport = {
    async assertNetwork() { gates++; },
    async lookup() { return stored; },
    async execute(data) {
      executions++;
      const persisted = JSON.parse((await state.read('phase-registry.json'))!);
      assert.equal(persisted.transactionBytes, Buffer.from(data).toString('base64'));
      assert.equal((await stat(join(state.directory, 'phase-registry.json'))).mode & 0o777, 0o600);
      stored = { $kind: 'Transaction', Transaction: { digest: TransactionDataBuilder.getDigestFromBytes(data), status: { success: true },
        effects: {}, objectTypes: {} } } as Awaited<ReturnType<PhaseTransport['lookup']>>;
      if (failure) throw new Error('synthetic response loss after chain acceptance');
    },
  };
  return { chain, executions: () => executions, gates: () => gates };
}
test('testnet endpoint rejects mainnet, localhost, credentials and mismatched genesis/network', async () => {
  const genesis = toBase58(new Uint8Array(32).fill(1));
  validateEndpoint('https://fullnode.testnet.sui.io:443', genesis);
  for (const url of ['https://fullnode.mainnet.sui.io', 'http://127.0.0.1:9000', 'https://x@fullnode.testnet.sui.io', 'https://fullnode.testnet.sui.io/?key=x']) {
    assert.throws(() => validateEndpoint(url, genesis));
  }
  for (const response of [{ chain: 'mainnet', chainId: genesis }, { chain: 'testnet', chainId: toBase58(new Uint8Array(32).fill(2)) }]) {
    await assert.rejects(verifyTestnet({ ledgerService: { async getServiceInfo() { return { response }; } } } as never, genesis));
  }
});
test('bytecode hash and production module count are mandatory; gas total is bounded', () => {
  const artifact = JSON.stringify({ modules: ['AQ==', 'Ag=='], dependencies: ['0x1', '0x2'], digest: Array(32).fill(1) });
  const digest = createHash('sha256').update(artifact).digest('hex');
  assert.equal(parseBytecode(artifact, digest).modules.length, 2);
  assert.throws(() => parseBytecode(`${artifact} `, digest));
  assert.throws(() => parseBytecode(artifact, '0'.repeat(64)));
  assert.equal(GAS_BUDGET, 100_000_000n); assert(MAX_TOTAL_GAS_BUDGET <= 1_000_000_000n);
  assert.equal(syntheticIssuerId('00000000-0000-4000-8000-000000000001').length, 66);
  assert.throws(() => syntheticIssuerId('real-school-internal-id'));
});
test('private state rejects permissive dirs, symlinks and overwrite', async t => {
  const state = await fixture(t);
  await state.create('sample.key', 'synthetic public test content');
  await assert.rejects(state.create('sample.key', 'overwrite'));
  await symlink(join(state.directory, 'sample.key'), join(state.directory, 'alias.key'));
  await assert.rejects(state.read('alias.key'));
  await chmod(join(state.directory, 'sample.key'), 0o644);
  await assert.rejects(state.read('sample.key'));
  await chmod(state.directory, 0o755); await assert.rejects(state.initialize());
});
test('signed bytes exist before submit and completed phase resumes without rebuilding or submitting', async t => {
  const state = await fixture(t), fake = new FakeChain(), rpc = transport(state), tx = transaction();
  await savedPhase(state, 'registry', { synthetic: true }, tx, testSigner, rpc.chain, () => fake.build(tx));
  const before = await readFile(join(state.directory, 'phase-registry.json'), 'utf8');
  await savedPhase(state, 'registry', { synthetic: true }, transaction(), testSigner, rpc.chain, async () => { throw new Error('must not rebuild'); });
  assert.equal(await readFile(join(state.directory, 'phase-registry.json'), 'utf8'), before);
  assert.equal(rpc.executions(), 1); assert.equal(rpc.gates(), 2);
});
test('ambiguous accepted submit resumes by digest and does not deploy a replacement', async t => {
  const state = await fixture(t), fake = new FakeChain(), rpc = transport(state, true), tx = transaction();
  await assert.rejects(savedPhase(state, 'registry', {}, tx, testSigner, rpc.chain, () => fake.build(tx)));
  await savedPhase(state, 'registry', {}, transaction(), testSigner, rpc.chain, async () => { throw new Error('must not rebuild'); });
  assert.equal(rpc.executions(), 1);
});
test('tampered intent and truncated saved bytes cannot submit', async t => {
  const state = await fixture(t), fake = new FakeChain(), rpc = transport(state), tx = transaction();
  await savedPhase(state, 'registry', {}, tx, testSigner, rpc.chain, () => fake.build(tx));
  await assert.rejects(savedPhase(state, 'registry', { changed: true }, transaction(), testSigner, rpc.chain, async () => { throw new Error('must not build'); }));
  await writeFile(join(state.directory, 'phase-registry.json'), '{', { mode: 0o600 });
  await assert.rejects(savedPhase(state, 'registry', {}, transaction(), testSigner, rpc.chain, async () => { throw new Error('must not build'); }));
  assert.equal(rpc.executions(), 1);
});
test('RPC gas budget increase is refused before signing or persistence', async t => {
  const state = await fixture(t), fake = new FakeChain(), rpc = transport(state), tx = transaction();
  await assert.rejects(savedPhase(state, 'registry', {}, tx, testSigner, rpc.chain, async () => {
    const altered = TransactionDataBuilder.fromBytes(await fake.build(tx));
    altered.gasData.budget = '100000001';
    return altered.build();
  }));
  assert.equal(await state.read('phase-registry.json'), null); assert.equal(rpc.executions(), 0);
});
test('offline RPC mutation of a pure input is refused before signing/persistence', async t => {
  const state = await fixture(t), fake = new FakeChain(), rpc = transport(state), tx = transaction();
  await assert.rejects(savedPhase(state, 'registry', {}, tx, testSigner, rpc.chain, async () => {
    const altered = TransactionDataBuilder.fromBytes(await fake.build(tx));
    altered.inputs[0] = { Pure: { bytes: 'BAQEBAQ=' }, $kind: 'Pure' };
    return altered.build();
  }));
  assert.equal(await state.read('phase-registry.json'), null); assert.equal(rpc.executions(), 0);
});
test('fresh UpgradeCap starts at version 1; zero, upgraded, wrong package and changed policy fail closed', () => {
  const cap = { id: h(21), package: h(22), version: '1', policy: 0 };
  assert.equal(validateInitialUpgradeCap(UpgradeCapBcs.serialize(cap).toBytes(), cap.id, cap.package).version, '1');
  for (const changed of [{ version: '0' }, { version: '2' }, { package: h(23) }, { policy: 128 }]) {
    assert.throws(() => validateInitialUpgradeCap(UpgradeCapBcs.serialize({ ...cap, ...changed }).toBytes(), cap.id, cap.package));
  }
});
test('safe CLI diagnostics expose only allowlisted code/name and source basename/line', () => {
  const output = safeDeploymentFailure({ code: 'DEPLOYMENT_REFUSED', name: 'Error',
    message: 'sensitive RPC body and key', stack: 'Error: sensitive RPC body\n at ensure (/secret/directory/deploy-testnet.ts:24:1)\n at deploy (/private/path/deploy-testnet.ts:245:7)' });
  assert.deepEqual(output, { ok: false, code: 'DEPLOYMENT_REFUSED', name: 'Error', source: 'deploy-testnet.ts:245:7' });
  const malformed = safeDeploymentFailure({ code: 'private-token', name: 'private-key', message: 'private-key', stack: 'private-key' });
  assert.deepEqual(malformed, { ok: false, code: 'DEPLOYMENT_FAILED', name: 'Error', source: 'deploy-testnet' });
  assert(!JSON.stringify(output).includes('secret'));
});
