import assert from 'node:assert/strict';
import test from 'node:test';
import { TestTransport } from '@protobuf-ts/runtime-rpc';
import { ObjectError, TransactionError } from '@mysten/sui/client';
import { SuiGrpcClient, GrpcTypes } from '@mysten/sui/grpc';
import { normalizeSuiAddress, toBase58 } from '@mysten/sui/utils';
import { bytes } from '../src/contracts.js';
import { GrpcChain } from '../src/sdk.js';
import { Clock } from '../src/codecs.js';
import { h, identity } from './fixtures.js';

const config = { identity, rpcUrl: 'https://not-contacted.invalid', timeoutMs: 1000 };
function adapter(response: object) {
  const transport = new TestTransport({ response });
  const client = new SuiGrpcClient({ network: 'testnet', transport });
  return { chain: new GrpcChain(config, client), client, transport };
}
test('real SuiGrpcClient adapter normalizes service genesis base58 to four-byte approval domain', async () => {
  const { chain } = adapter({ chainId: toBase58(bytes(`0x${identity.chainIdentifier}${'00'.repeat(28)}`)), chain: 'testnet' });
  assert.equal(await chain.chainIdentifier(), identity.chainIdentifier);
});
test('real SDK service network name cannot relabel mainnet as testnet', async () => {
  const { chain } = adapter({ chainId: toBase58(bytes(h(1))), chain: 'mainnet' });
  await assert.rejects(() => chain.chainIdentifier(), /network name differs/);
});
test('Clock parser checks canonical object identity and converts milliseconds to exact seconds', async () => {
  const { chain } = adapter({}), clockId = normalizeSuiAddress('0x6');
  chain.object = async objectId => ({ objectId, shared: true, type: '0x2::clock::Clock',
    content: Clock.serialize({ id: clockId, timestamp_ms: '1800000000999' }).toBytes() });
  assert.equal(await chain.clockSeconds(), 1800000000n);
  chain.object = async objectId => ({ objectId, shared: true, type: '0x2::clock::Clock',
    content: Clock.serialize({ id: h(9), timestamp_ms: '1800000000999' }).toBytes() });
  await assert.rejects(() => chain.clockSeconds(), /UID is inconsistent/);
});
test('real SDK checkpoint request and response preserve exact u64 sequence and base58 digest', async () => {
  const { chain, transport } = adapter({ checkpoint: GrpcTypes.Checkpoint.create({ sequenceNumber: 25n, digest: toBase58(bytes(h(3))) }) });
  assert.deepEqual(await chain.checkpoint('25'), { sequence: '25', digest: toBase58(bytes(h(3))) });
  assert.equal(transport.sentMessages[0].checkpointId.sequenceNumber, 25n);
});
test('adapter maps only typed notFound errors to absence, never deleted/unknown or transport failures', async () => {
  const { chain, client } = adapter({});
  client.getDynamicField = async () => { throw new ObjectError('NOT_FOUND', 'missing', { reason: 'notFound' }); };
  assert.equal(await chain.field(h(1), 'u64', new Uint8Array(8)), null);
  for (const reason of ['deleted', 'unknown'] as const) {
    client.getDynamicField = async () => { throw new ObjectError('INTERNAL', 'unavailable', { reason }); };
    await assert.rejects(() => chain.field(h(1), 'u64', new Uint8Array(8)));
  }
  client.getTransaction = async () => { throw new TransactionError('notFound', toBase58(bytes(h(1)))); };
  assert.equal(await chain.transaction(toBase58(bytes(h(1)))), null);
  client.getTransaction = async () => { throw new Error('unavailable'); };
  await assert.rejects(() => chain.transaction(toBase58(bytes(h(1)))));
});
test('real SDK transaction normalization distinguishes success/failed and preserves checkpoint', async () => {
  const digest = toBase58(bytes(h(1)));
  const { chain } = adapter({ transaction: GrpcTypes.ExecutedTransaction.create({ digest, checkpoint: 25n,
    effects: { status: { success: true }, epoch: 2n }, events: { events: [] } }) });
  const tx = await chain.transaction(digest);
  assert.equal(tx?.digest, digest); assert.equal(tx?.checkpoint, '25'); assert.equal(tx?.success, true);
});
