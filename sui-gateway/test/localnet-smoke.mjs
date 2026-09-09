// Explicit opt-in localnet integration test. No public RPC/faucet URL is accepted.
// Run with: node --import tsx test/localnet-smoke.mjs <task-local-output-directory>
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { mkdir, open, readFile } from 'node:fs/promises';
import { spawnSync } from 'node:child_process';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { SuiGrpcClient } from '@mysten/sui/grpc';
import { Ed25519Keypair } from '@mysten/sui/keypairs/ed25519';
import { Transaction } from '@mysten/sui/transactions';
import { requestSuiFromFaucetV2 } from '@mysten/sui/faucet';
import { fromBase58 } from '@mysten/sui/utils';
import { secp256k1 } from '@noble/curves/secp256k1.js';
import { keccak_256 } from '@noble/hashes/sha3.js';
import { Gateway } from '../src/gateway.ts';
import { GrpcChain } from '../src/sdk.ts';
import { DurableJournal } from '../src/journal.ts';
import { createGatewayServer } from '../src/server.ts';
import { signIssuer } from '../src/approval.ts';

const rpcUrl = 'http://127.0.0.1:19670';
const faucetUrl = 'http://127.0.0.1:19671';
const gatewayUrl = 'http://127.0.0.1:19672';
const directory = resolve(process.argv[2] ?? '');
assert(directory.startsWith('/private/tmp/trekkey-sui-localnet.') && directory !== '/private/tmp/trekkey-sui-localnet.');
assert(process.env.SUI_CONFIG_DIR?.startsWith('/private/tmp/'));
assert(process.env.MOVE_HOME?.startsWith('/private/tmp/'));
assert(process.env.TMPDIR?.startsWith('/private/tmp/trekkey-sui-localnet.'));
const cli = process.env.TREKKEY_LOCAL_SUI_CLI;
assert(cli?.startsWith('/private/tmp/') && cli.endsWith('/sui'));
const hex = value => `0x${Buffer.from(value).toString('hex')}`;
const hash = byte => `0x${byte.repeat(32)}`;
const delay = ms => new Promise(done => setTimeout(done, ms));
let stage = 'identity';
let server;
const result = { network: 'localnet', rpcUrl, faucetUrl, gatewayUrl, transactions: {} };
const token = randomBytes(32).toString('hex');
const relayer = Ed25519Keypair.generate(); // Memory only; never exported or printed.
const issuerSecret = randomBytes(32); // Memory only; never exported or printed.
const issuerSigner = hex(keccak_256(secp256k1.getPublicKey(issuerSecret, false).slice(1)).slice(12));
const client = new SuiGrpcClient({ network: 'localnet', baseUrl: rpcUrl, timeout: 15000 });

async function persist(name, value) {
  const file = await open(resolve(directory, name), 'wx', 0o600);
  try { await file.writeFile(JSON.stringify(value, null, 2)); await file.sync(); }
  finally { await file.close(); }
}
async function execute(tx, name) {
  tx.setGasBudget(100_000_000);
  const response = await client.signAndExecuteTransaction({ transaction: tx, signer: relayer,
    include: { effects: true, events: true, objectTypes: true } });
  const transaction = response.Transaction ?? response.FailedTransaction;
  assert.equal(transaction.status.success, true, `${name}: local Move execution failed`);
  result.transactions[name] = transaction.digest;
  await client.waitForTransaction({ digest: transaction.digest });
  console.log(JSON.stringify({ stage: name, digest: transaction.digest }));
  return transaction;
}
async function request(path, value) {
  const response = await fetch(`${gatewayUrl}/v1/${path}`, { method: value === undefined ? 'GET' : 'POST',
    headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json' },
    body: value === undefined ? undefined : JSON.stringify(value), signal: AbortSignal.timeout(20000) });
  const body = await response.json();
  if (!body.ok) { const error = new Error('Gateway smoke operation failed'); error.code = body.error?.code; throw error; }
  return body.data;
}
async function submitPrepared(kind, approval, operationType) {
  const input = { approval, issuerSignature: signIssuer(result.identity, approval, issuerSecret) };
  const prepared = await request(`prepare-${kind}`, input);
  await persist(`${operationType}-prepared.json`, prepared); // Durable caller boundary BEFORE broadcast.
  const replay = await request(`prepare-${kind}`, input);
  assert.equal(replay.signedRawTransaction, prepared.signedRawTransaction);
  const saved = JSON.parse(await readFile(resolve(directory, `${operationType}-prepared.json`), 'utf8'));
  const broadcast = await request('broadcast', saved);
  assert.equal(broadcast.transactionDigest, saved.transactionDigest);
  for (let attempt = 0; attempt < 40; attempt++) {
    const receipt = await request('receipt', { transactionHash: saved.transactionHash, operationType });
    if (receipt.state !== 'PENDING') {
      assert.equal(receipt.state, 'CONFIRMED');
      assert(receipt.checkpointDigest && receipt.eventLogIndex >= 0);
      result.transactions[operationType] = saved.transactionDigest;
      result[`${operationType}Receipt`] = receipt;
      console.log(JSON.stringify({ stage: operationType, digest: saved.transactionDigest, checkpoint: receipt.blockNumber }));
      return;
    }
    await delay(500);
  }
  throw new Error('Receipt confirmation timeout');
}

try {
  await mkdir(directory, { recursive: true, mode: 0o700 });
  const { response: service } = await client.ledgerService.getServiceInfo({});
  assert.equal(service.chain, 'unknown');
  const chainIdentifier = Buffer.from(fromBase58(service.chainId).slice(0, 4)).toString('hex');
  result.genesisDigest = service.chainId;
  stage = 'local-faucet';
  const faucet = await requestSuiFromFaucetV2({ host: faucetUrl, recipient: relayer.toSuiAddress() });
  assert.equal(faucet.status, 'Success');
  stage = 'offline-build';
  const build = spawnSync(cli, ['move', 'build', '--dump-bytecode-as-base64', '--no-tree-shaking'], {
    cwd: fileURLToPath(new URL('../../contracts-sui/', import.meta.url)), env: process.env,
    encoding: 'utf8', maxBuffer: 16_000_000, timeout: 45000,
  });
  assert.equal(build.status, 0, 'Offline Move build failed');
  const compiled = JSON.parse(build.stdout);
  stage = 'publish-local';
  const publish = new Transaction();
  const upgradeCap = publish.publish({ modules: compiled.modules, dependencies: compiled.dependencies });
  publish.transferObjects([upgradeCap], relayer.toSuiAddress());
  const published = await execute(publish, 'publish');
  const packageId = published.effects.changedObjects.find(item => item.outputState === 'PackageWrite')?.objectId;
  assert(packageId);
  stage = 'create-registry';
  const create = new Transaction();
  create.moveCall({ target: `${packageId}::credential_registry::create_registry`,
    arguments: [create.pure.vector('u8', Buffer.from(chainIdentifier, 'hex'))] });
  const created = await execute(create, 'createRegistry');
  const registryId = Object.entries(created.objectTypes).find(([, type]) => type === `${packageId}::credential_registry::Registry`)?.[0];
  const adminCapId = Object.entries(created.objectTypes).find(([, type]) => type === `${packageId}::credential_registry::AdminCap`)?.[0];
  assert(registryId && adminCapId);
  result.identity = { network: 'localnet', chainIdentifier, packageId, registryId, protocolVersion: 1 };
  stage = 'authorize-local';
  const authorize = new Transaction();
  authorize.moveCall({ target: `${packageId}::credential_registry::set_relayer`, arguments: [
    authorize.object(registryId), authorize.object(adminCapId), authorize.pure.address(relayer.toSuiAddress()), authorize.pure.bool(true)] });
  authorize.moveCall({ target: `${packageId}::credential_registry::register_issuer_key`, arguments: [
    authorize.object(registryId), authorize.object(adminCapId), authorize.object('0x6'),
    authorize.pure.vector('u8', Buffer.from(hash('33').slice(2), 'hex')), authorize.pure.u64(1),
    authorize.pure.vector('u8', Buffer.from(issuerSigner.slice(2), 'hex'))] });
  await execute(authorize, 'authorize');
  stage = 'gateway-initialize';
  const chain = new GrpcChain({ identity: result.identity, rpcUrl, timeoutMs: 15000 });
  const originalBuild = chain.build.bind(chain);
  chain.build = async transaction => {
    try { return await originalBuild(transaction); }
    catch (error) {
      // Only a Move abort location/code is allowed through; no arbitrary RPC body or transaction bytes.
      const moveAbort = String(error.message).match(/MoveAbort[\s\S]*/)?.[0].slice(-200);
      console.error(JSON.stringify({ stage: 'local-simulation', code: error.code ?? error.name, moveAbort }));
      throw error;
    }
  };
  const gateway = new Gateway(result.identity, chain, relayer, new DurableJournal(resolve(directory, 'journal')));
  await gateway.initialize();
  server = createGatewayServer(gateway, token);
  await new Promise((done, reject) => { server.once('error', reject); server.listen(19672, '127.0.0.1', done); });
  assert.deepEqual(await request('identity'), result.identity);
  assert.equal((await request('issuer-key', { issuerId: hash('33'), keyVersion: '1' })).signer, issuerSigner);
  assert.equal((await request('batch', { batchIdHash: hash('44') })).exists, false);
  const deadline = String(Math.floor(Date.now() / 1000) + 180);
  stage = 'prepare-persist-broadcast-anchor';
  await submitPrepared('batch', { issuerId: hash('33'), batchIdHash: hash('44'), merkleRoot: hash('55'),
    schemaVersionHash: hash('66'), leafCount: '3', treeVersion: '1', issuerKeyVersion: '1', approvalNonce: '7', deadline }, 'ANCHOR_BATCH');
  result.batch = await request('batch', { batchIdHash: hash('44') });
  assert.equal(result.batch.exists, true);
  assert.equal(result.batch.merkleRoot, hash('55'));
  stage = 'prepare-persist-broadcast-revoke';
  await submitPrepared('status', { issuerId: hash('33'), credentialIdHash: hash('77'), action: '0',
    replacementCredentialIdHash: hash('00'), effectiveAt: result.batch.anchoredAt,
    issuerKeyVersion: '1', approvalNonce: '8', deadline }, 'REVOKE');
  result.status = await request('status', { issuerId: hash('33'), credentialIdHash: hash('77') });
  assert.equal(result.status.state, 'REVOKED');
  result.ok = true;
  await persist('smoke-result.json', result);
  console.log(JSON.stringify({ ok: true, identity: result.identity, transactions: result.transactions }));
} catch (error) {
  console.error(JSON.stringify({ ok: false, stage, code: error.code ?? error.name ?? 'LOCALNET_SMOKE_FAILED' }));
  process.exitCode = 1;
} finally {
  issuerSecret.fill(0);
  if (server) { server.closeAllConnections(); await new Promise(done => server.close(done)); }
}
