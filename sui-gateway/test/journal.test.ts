import assert from 'node:assert/strict';
import { readFile, readdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import test from 'node:test';
import { secp256k1 } from '@noble/curves/secp256k1.js';
import { approvalDigest, signIssuer } from '../src/approval.js';
import { bytes, GatewayError, hexBytes, parseBatch, ZERO32 } from '../src/contracts.js';
import { DurableJournal } from '../src/journal.js';
import { fixture, identity, journalDirectory, setup } from './fixtures.js';

const request = (nonce = '7') => {
  const approval = parseBatch({ ...fixture.batch.approval, approvalNonce: nonce });
  return { approval, issuerSignature: signIssuer(identity, approval, bytes(`0x${'00'.repeat(31)}01`)) };
};
const errorCode = (code: string) => (error: unknown) => error instanceof GatewayError && error.code === code;
test('same approval is prepare-idempotent concurrently and after a new gateway/journal instance restarts', async () => {
  const dir = journalDirectory(), first = setup({}, new DurableJournal(dir));
  const [a, b] = await Promise.all([first.gateway.prepareBatch(request()), first.gateway.prepareBatch(request())]);
  assert.deepEqual(a, b); assert.equal(first.chain.buildCount, 1); assert.equal(first.chain.executeCount, 0);
  const restarted = setup({}, new DurableJournal(dir));
  assert.deepEqual(await restarted.gateway.prepareBatch(request()), a);
  assert.equal(restarted.chain.buildCount, 0);
  // Revocation of the issuer does not erase the already-signed exact transaction needed for reconciliation.
  restarted.chain.issuer.compromised_at = '1800000000';
  assert.deepEqual(await restarted.gateway.prepareBatch(request()), a);
});
test('a different valid low-s issuer signature for identical fields returns the original prepared envelope', async () => {
  const state = setup(), input = request(), first = await state.gateway.prepareBatch(input);
  const sig = secp256k1.sign(approvalDigest(identity, input.approval), bytes(`0x${'00'.repeat(31)}01`),
    { prehash: false, lowS: true, format: 'recovered', extraEntropy: new Uint8Array(32).fill(1) });
  const alternate = hexBytes(Uint8Array.from([...sig.slice(1), sig[0]! + 27]));
  assert.notEqual(alternate, input.issuerSignature);
  assert.deepEqual(await state.gateway.prepareBatch({ ...input, issuerSignature: alternate }), first);
  assert.equal(state.chain.buildCount, 1);
});
test('independent journal instances cannot sign different approvals for the same gas object version', async () => {
  const dir = journalDirectory(); let signatures = 0;
  const observer = (point: string) => { if (point === 'beforeSignedPersist') signatures++; };
  const a = setup({}, new DurableJournal(dir, observer)), b = setup({}, new DurableJournal(dir, observer));
  const results = await Promise.allSettled([a.gateway.prepareBatch(request('7')), b.gateway.prepareBatch(request('8'))]);
  assert.equal(results.filter(r => r.status === 'fulfilled').length, 1);
  assert.equal(signatures, 1);
  const failed = results.find(r => r.status === 'rejected');
  assert(failed?.status === 'rejected' && errorCode('BLOCKCHAIN_GAS_RESERVED')(failed.reason));
});
test('a crash after signing but before durable response resumes the identical unsigned bytes without rebuilding', async () => {
  const dir = journalDirectory(), crash = setup({}, new DurableJournal(dir, point => {
    if (point === 'beforeSignedPersist') throw new Error('synthetic crash before signed persistence');
  }));
  await assert.rejects(() => crash.gateway.prepareBatch(request()), /synthetic crash/);
  const approvalFile = (await readdir(dir)).find(name => name.startsWith('approval-') && name.endsWith('.json'))!;
  const saved = JSON.parse(await readFile(join(dir, approvalFile), 'utf8'));
  assert.equal(saved.stage, 'UNSIGNED');
  const restart = setup({}, new DurableJournal(dir)), prepared = await restart.gateway.prepareBatch(request());
  const envelope = JSON.parse(Buffer.from(prepared.signedRawTransaction, 'base64').toString('utf8'));
  assert.equal(envelope.transactionBytes, saved.transactionBytes);
  assert.equal(envelope.transactionDigest, saved.transactionDigest);
  assert.equal(restart.chain.buildCount, 0);
  assert.equal(JSON.parse(await readFile(join(dir, approvalFile), 'utf8')).stage, 'SIGNED');
});
test('a crash after bytes persisted but before gas reservation recovers only those same bytes', async () => {
  const dir = journalDirectory(), crash = setup({}, new DurableJournal(dir, point => {
    if (point === 'afterUnsignedPersist') throw new Error('synthetic crash');
  }));
  await assert.rejects(() => crash.gateway.prepareBatch(request()));
  const restarted = setup({}, new DurableJournal(dir));
  await restarted.gateway.prepareBatch(request());
  assert.equal(restarted.chain.buildCount, 0);
});
test('a crash before unsigned bytes persist leaves an explicit manual-recovery state, never a new build', async () => {
  const dir = journalDirectory(), crash = setup({}, new DurableJournal(dir, point => {
    if (point === 'afterBuild') throw new Error('synthetic crash');
  }));
  await assert.rejects(() => crash.gateway.prepareBatch(request()));
  const restarted = setup({}, new DurableJournal(dir));
  await assert.rejects(() => restarted.gateway.prepareBatch(request()), errorCode('BLOCKCHAIN_JOURNAL_RECOVERY_REQUIRED'));
  assert.equal(restarted.chain.buildCount, 0);
});
test('invalid new approvals never create a digest claim that can deny a legitimate approval later', async () => {
  const dir = journalDirectory(), state = setup({}, new DurableJournal(dir));
  state.chain.issuer.signer = bytes(`0x${'99'.repeat(20)}`);
  await assert.rejects(() => state.gateway.prepareBatch(request()), errorCode('BLOCKCHAIN_APPROVAL_INVALID'));
  assert.deepEqual(await readdir(dir), []);
});
test('a host/chain clock gap is retryable before any durable claim, and the same approval works after the chain catches up', async () => {
  const dir = journalDirectory(), state = setup({}, new DurableJournal(dir));
  state.chain.clock = 1799999999n;
  const input = { approval: fixture.status.approval, issuerSignature: fixture.status.signature };
  await assert.rejects(() => state.gateway.prepareStatus(input), error => errorCode('BLOCKCHAIN_CHAIN_CLOCK_BEHIND')(error) && (error as GatewayError).retryable);
  assert.deepEqual(await readdir(dir), []);
  assert.equal(state.chain.buildCount, 0);
  state.chain.clock = 1800000000n;
  await state.gateway.prepareStatus(input);
  assert.equal(state.chain.buildCount, 1);
});
test('gas reservations have no TTL; a fresh on-chain gas version can serve a new approval', async () => {
  const dir = journalDirectory(), first = setup({}, new DurableJournal(dir));
  await first.gateway.prepareBatch(request('7'));
  const next = setup({}, new DurableJournal(dir)); next.chain.gasVersion = '2';
  await next.gateway.prepareBatch(request('8'));
  const reservations = (await readdir(dir)).filter(name => name.startsWith('gas-'));
  assert.equal(reservations.length, 2);
});
test('tampered journal transaction bytes cannot trigger rebuilding or signing', async () => {
  const dir = journalDirectory(), first = setup({}, new DurableJournal(dir));
  await first.gateway.prepareBatch(request());
  const digest = hexBytes(approvalDigest(identity, request().approval));
  const file = join(dir, `approval-${digest.slice(2)}.json`), saved = JSON.parse(await readFile(file, 'utf8'));
  saved.transactionDigest = ZERO32;
  await writeFile(file, JSON.stringify(saved)); // Synthetic owned fixture only.
  const next = setup({}, new DurableJournal(dir));
  await assert.rejects(() => next.gateway.prepareBatch(request()), errorCode('BLOCKCHAIN_JOURNAL_RECOVERY_REQUIRED'));
  assert.equal(next.chain.buildCount, 0);
});
