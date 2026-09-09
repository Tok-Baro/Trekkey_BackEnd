import assert from 'node:assert/strict';
import test from 'node:test';
import { TransactionDataBuilder } from '@mysten/sui/transactions';
import { bytes, GatewayError, type Prepared } from '../src/contracts.js';
import { digestToHash, hashToDigest } from '../src/gateway.js';
import { event, fixture, h, identity, setup, testSigner } from './fixtures.js';

const request = () => ({ approval: fixture.batch.approval, issuerSignature: fixture.batch.signature });
const code = (name: string) => (error: unknown) => error instanceof GatewayError && error.code === name;
function modifyEnvelope(prepared: Prepared, change: (e: Record<string, unknown>) => void) {
  const envelope = JSON.parse(Buffer.from(prepared.signedRawTransaction, 'base64').toString('utf8'));
  change(envelope);
  return { ...prepared, signedRawTransaction: Buffer.from(JSON.stringify(envelope)).toString('base64') };
}

test('prepare resolves/builds and Ed25519 signs, has null nonce, but never broadcasts', async () => {
  const { gateway, chain } = setup();
  assert.deepEqual(await gateway.initialize(), identity);
  const prepared = await gateway.prepareBatch(request());
  assert.equal(chain.buildCount, 1);
  assert.equal(chain.executeCount, 0);
  assert.equal(prepared.transactionNonce, null);
  assert.equal(prepared.relayerAddress, testSigner.toSuiAddress());
  assert.equal(digestToHash(prepared.transactionDigest), prepared.transactionHash);
  const envelope = JSON.parse(Buffer.from(prepared.signedRawTransaction, 'base64').toString('utf8'));
  assert.equal(envelope.operationType, 'ANCHOR_BATCH');
  assert.equal(TransactionDataBuilder.getDigestFromBytes(Buffer.from(envelope.transactionBytes, 'base64')), prepared.transactionDigest);
});
test('broadcast and retries execute exactly the persisted signed transaction without rebuilding', async () => {
  const { gateway, chain } = setup();
  const prepared = await gateway.prepareBatch(request());
  await gateway.broadcast(prepared);
  await gateway.broadcast(prepared);
  assert.equal(chain.buildCount, 1);
  assert.equal(chain.executeCount, 2);
  assert.deepEqual(chain.submitted[0], chain.submitted[1]);
});
test('broadcast rejects tampered hash, signature, identity, operation and malformed envelope before execute', async () => {
  const { gateway, chain } = setup(), prepared = await gateway.prepareBatch(request());
  const cases = [
    { ...prepared, transactionHash: h(3) },
    { ...prepared, signedRawTransaction: 'invalid!' },
    modifyEnvelope(prepared, e => { e.signature = Buffer.from(new Uint8Array(97)).toString('base64'); }),
    modifyEnvelope(prepared, e => { e.identity = { ...identity, registryId: h(1) }; }),
    modifyEnvelope(prepared, e => { e.operationType = 'REVOKE'; }),
    modifyEnvelope(prepared, e => { e.relayerAddress = h(7); }),
  ];
  for (const input of cases) await assert.rejects(() => gateway.broadcast(input));
  assert.equal(chain.executeCount, 0);
});
test('mainnet prepare and broadcast are blocked independently of HTTP or Java authorization', async () => {
  const { gateway, chain } = setup({ network: 'mainnet' });
  await assert.rejects(() => gateway.prepareBatch(request()), code('BLOCKCHAIN_WRITES_DISABLED'));
  await assert.rejects(() => gateway.broadcast({}), code('BLOCKCHAIN_WRITES_DISABLED'));
  assert.equal(chain.buildCount + chain.executeCount, 0);
});
test('endpoint/registry identity changes block both prepare and later broadcast', async () => {
  const { gateway, chain } = setup(), prepared = await gateway.prepareBatch(request());
  chain.chainId = '00000001';
  await assert.rejects(() => gateway.broadcast(prepared), code('BLOCKCHAIN_IDENTITY_MISMATCH'));
  chain.chainId = identity.chainIdentifier;
  chain.registry.package_id = h(5);
  await assert.rejects(() => gateway.prepareBatch(request()), code('BLOCKCHAIN_IDENTITY_MISMATCH'));
  assert.equal(chain.executeCount, 0);
});
test('missing/retired/compromised issuer, wrong signature, unauthorized relayer and paused registry fail before signing', async () => {
  for (const mutate of [
    (s: ReturnType<typeof setup>) => { s.chain.issuerExists = false; },
    (s: ReturnType<typeof setup>) => { s.chain.issuer.valid_until = '1750000000'; },
    (s: ReturnType<typeof setup>) => { s.chain.issuer.compromised_at = '1750000000'; },
    (s: ReturnType<typeof setup>) => { s.chain.issuer.signer = bytes(`0x${'22'.repeat(20)}`); },
    (s: ReturnType<typeof setup>) => { s.chain.relayerAllowed = false; },
    (s: ReturnType<typeof setup>) => { s.chain.registry.paused = true; },
  ]) {
    const state = setup(); mutate(state);
    await assert.rejects(() => state.gateway.prepareBatch(request()));
    assert.equal(state.chain.buildCount + state.chain.executeCount, 0);
  }
});
test('expired approval is rejected before building', async () => {
  const { gateway, chain } = setup();
  await assert.rejects(() => gateway.prepareBatch({ ...request(), approval: { ...fixture.batch.approval, deadline: '1' } }), code('BLOCKCHAIN_APPROVAL_EXPIRED'));
  assert.equal(chain.buildCount, 0);
});
test('safe absence differs from RPC failure for issuer, batch and credential status', async () => {
  const { gateway, chain } = setup(); chain.issuerExists = false;
  assert.equal((await gateway.issuerKey({ issuerId: h(1), keyVersion: '1' })).exists, false);
  assert.equal((await gateway.batch({ batchIdHash: h(1) })).exists, false);
  assert.equal((await gateway.status({ issuerId: h(1), credentialIdHash: h(2) })).state, 'NONE');
  chain.fieldFailure = new Error('transport unavailable');
  await assert.rejects(() => gateway.batch({ batchIdHash: h(1) }));
});
test('broadcast network uncertainty stays retryable and execution rejection is terminal', async () => {
  const { gateway, chain } = setup(), prepared = await gateway.prepareBatch(request());
  chain.executeFailure = new Error('socket closed AFTER transmission');
  await assert.rejects(() => gateway.broadcast(prepared), error => code('BLOCKCHAIN_BROADCAST_AMBIGUOUS')(error) && (error as GatewayError).retryable);
  chain.executeFailure = null; chain.execSuccess = false;
  await assert.rejects(() => gateway.broadcast(prepared), code('BLOCKCHAIN_BROADCAST_REJECTED'));
});
test('receipt confirms only exact operation event and actual checkpoint coordinates', async () => {
  const { gateway, chain } = setup(), digest = hashToDigest(h(3));
  const input = { transactionHash: h(3), operationType: 'ANCHOR_BATCH' };
  assert.equal((await gateway.receipt(input)).state, 'PENDING');
  chain.tx = { digest, checkpoint: null, success: true, events: [event()] };
  assert.equal((await gateway.receipt(input)).state, 'PENDING');
  chain.tx.checkpoint = '25';
  const receipt = await gateway.receipt(input);
  assert.equal(receipt.state, 'CONFIRMED'); assert.equal(receipt.blockNumber, '25');
  assert.equal(receipt.blockHash, digestToHash(chain.checkpointDigest));
  assert.equal(receipt.checkpointDigest, chain.checkpointDigest); assert.equal(receipt.eventLogIndex, 0);
  chain.tx.success = false;
  assert.equal((await gateway.receipt(input)).state, 'REVERTED');
});
test('receipt rejects other package/module/registry/type, missing or duplicate event', async () => {
  const { gateway, chain } = setup();
  for (const events of [[], [event(h(7))], [{ ...event(), packageId: h(7) }], [{ ...event(), module: 'other' }],
    [{ ...event(), eventType: `${identity.packageId}::credential_registry::CredentialRevoked` }], [event(), event()]]) {
    chain.tx = { digest: hashToDigest(h(3)), checkpoint: '25', success: true, events };
    await assert.rejects(() => gateway.receipt({ transactionHash: h(3), operationType: 'ANCHOR_BATCH' }), code('BLOCKCHAIN_RECEIPT_EVIDENCE_MISSING'));
  }
});
test('receipt rejects mismatched transaction digest/checkpoint sequence and malformed event BCS', async () => {
  const { gateway, chain } = setup(), input = { transactionHash: h(3), operationType: 'ANCHOR_BATCH' };
  chain.tx = { digest: hashToDigest(h(4)), checkpoint: '25', success: true, events: [event()] };
  await assert.rejects(() => gateway.receipt(input), code('BLOCKCHAIN_RESPONSE_INVALID'));
  chain.tx.digest = hashToDigest(h(3)); chain.checkpointSequence = '26';
  await assert.rejects(() => gateway.receipt(input), code('BLOCKCHAIN_RESPONSE_INVALID'));
  chain.checkpointSequence = '25'; chain.tx.events = [{ ...event(), bcs: new Uint8Array(32) }];
  await assert.rejects(() => gateway.receipt(input), code('BLOCKCHAIN_RECEIPT_EVIDENCE_MISSING'));
});
test('status preparation uses correct supersede function with issuer65, without broadcast', async () => {
  const { gateway, chain } = setup();
  const prepared = await gateway.prepareStatus({ approval: fixture.status.approval, issuerSignature: fixture.status.signature });
  const envelope = JSON.parse(Buffer.from(prepared.signedRawTransaction, 'base64').toString('utf8'));
  assert.equal(envelope.operationType, 'SUPERSEDE');
  await gateway.broadcast(prepared);
  assert.equal(chain.buildCount, 1); assert.equal(chain.executeCount, 1);
});
test('pause blocks only new anchoring, preserving emergency status operations as the Move contract does', async () => {
  const { gateway, chain } = setup(); chain.registry.paused = true;
  await assert.rejects(() => gateway.prepareBatch(request()), code('BLOCKCHAIN_REGISTRY_PAUSED'));
  await gateway.prepareStatus({ approval: fixture.status.approval, issuerSignature: fixture.status.signature });
  assert.equal(chain.buildCount, 1); assert.equal(chain.executeCount, 0);
});
test('untrusted transaction resolution may not alter issuer-approved pure arguments before signing', async () => {
  const { gateway, chain } = setup(), originalBuild = chain.build.bind(chain);
  chain.build = async tx => {
    const built = TransactionDataBuilder.fromBytes(await originalBuild(tx));
    built.inputs[2] = { $kind: 'Pure', Pure: { bytes: Buffer.from(new Uint8Array(33)).toString('base64') } };
    return built.build();
  };
  await assert.rejects(() => gateway.prepareBatch(request()), code('BLOCKCHAIN_PREPARED_INVALID'));
  assert.equal(chain.executeCount, 0);
});
