import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, writeFile, stat, chmod, symlink, rm } from 'node:fs/promises';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { CLAIMS, capture, makeClient, parseArguments, privateJson, projectResponse,
  validateIds, validateReference, verifyReference, writeReference } from './verify-legacy-http.mjs';

const ids = Array.from({ length: 6 }, (_, i) => `00000000-0000-4000-8000-${String(i + 1).padStart(12, '0')}`);
const hash = `0x${'11'.repeat(32)}`;
function payload(id, migrated = true) {
  return { data: {
    credentialPublicId: id, verificationStatus: 'VALID', credentialType: 'AWARD', schemaProfileId: 'trekkey:award:v1:jcs-rfc8785:unicode-nfc-1',
    issuerPublicId: ids[0], issuerName: 'NEVER_STORE_NAME', credentialNo: 'NEVER_STORE_NUMBER',
    issuedAt: '2026-09-08T12:00:00Z', expiresAt: null,
    publicSubjects: [{ subjectRef: migrated ? `public-subject:${id}:0` : 'user:42', displayName: 'NEVER_STORE_SUBJECT' }],
    evidence: { ...Object.fromEntries(CLAIMS.map(key => [key, true])),
      ...Object.fromEntries(['issuerId', 'credentialIdHash', 'schemaVersionHash', 'contentHash', 'fileManifestHash',
        'leafHash', 'batchIdHash', 'merkleRoot'].map(key => [key, hash])),
      batchPublicId: ids[1], treeVersion: 1, merkleProof: [hash], chainId: 1001,
      contractAddress: `0x${'22'.repeat(20)}`, transactionHash: hash, blockNumber: 123,
      ...(migrated ? { blockchain: { provider: 'KAIA', network: 'kairos', approvalScheme: 'EIP712_V1' } } : {}) }
  } };
}
const json = value => new Response(JSON.stringify(value), { status: 200 });
function transport(migrated, calls = []) {
  return async (url, options) => {
    calls.push({ url, options });
    if (url.endsWith('/certificate')) return new Response('%PDF-synthetic');
    if (url.endsWith('/package')) return new Response(Buffer.from([0x50, 0x4b, 0x03, 0x04, 1]));
    return json(payload(url.split('/').at(-1), migrated));
  };
}

test('capture stores only the immutable projection and verify allows alias migration with 18 bounded GETs', async () => {
  const calls = [];
  const reference = await capture(ids, transport(false, calls));
  assert.equal(calls.length, 6);
  assert.ok(calls.every(call => call.url.startsWith('http://127.0.0.1:8080/api/public/credentials/')));
  assert.doesNotMatch(JSON.stringify(reference), /NEVER_STORE|user:42|publicSubjects|issuerName|credentialNo/);
  const verified = [];
  assert.deepEqual(await verifyReference(reference, transport(true, verified)),
    { credentials: 6, jsonChecks: 6, binaryChecks: 12, passed: true });
  assert.equal(verified.length, 18);
  assert.ok(verified.every(call => call.url.startsWith('http://127.0.0.1:18082/')
    && call.options.method === 'GET' && call.options.redirect === 'error'
    && call.options.credentials === 'omit' && call.options.signal instanceof AbortSignal));
});

test('unknown statuses, provider, false claims, wrong identity and raw subject IDs fail closed', () => {
  const mutations = [p => p.data.verificationStatus = 'UNKNOWN', p => p.data.evidence.contentHashMatches = false,
    p => p.data.evidence.blockchain.provider = 'SUI', p => p.data.credentialPublicId = ids[1],
    p => p.data.publicSubjects[0].subjectRef = 'user:42', p => p.data.evidence.chainId = 0];
  for (const mutate of mutations) {
    const p = payload(ids[0]); mutate(p);
    assert.throws(() => projectResponse(p, ids[0], 'verify'));
  }
});

test('changed content hash rejects the replacement before binary requests', async () => {
  const reference = await capture(ids, transport(false));
  await assert.rejects(() => verifyReference(reference, async url => {
    const p = payload(url.split('/').at(-1)); p.data.evidence.contentHash = `0x${'33'.repeat(32)}`; return json(p);
  }), /PUBLIC_CONTRACT_MISMATCH/);
});

test('EIP-55 address casing preserves byte identity but a single address digit mutation still fails', async () => {
  const checksum = '0x52908400098527886E0F7030069857D2E4169EE7';
  const atAddress = (migrated, address) => async (url, options) => {
    if (url.endsWith('/certificate') || url.endsWith('/package')) return transport(migrated)(url, options);
    const response = payload(url.split('/').at(-1), migrated);
    response.data.evidence.contractAddress = address;
    return json(response);
  };
  const reference = await capture(ids, atAddress(false, checksum));
  assert.ok(reference.records.every(record => record.evidence.contractAddress === checksum.toLowerCase()));
  assert.equal((await verifyReference(reference, atAddress(true, checksum.toLowerCase()))).passed, true);
  assert.equal((await verifyReference(reference, atAddress(true, checksum))).passed, true);
  await assert.rejects(() => verifyReference(reference, atAddress(true, `${checksum.slice(0, -1)}8`)),
    /PUBLIC_CONTRACT_MISMATCH/);
  for (const malformed of [` ${checksum}`, `${checksum}0`, `${checksum.slice(0, -1)}G`]) {
    await assert.rejects(() => capture(ids, atAddress(false, malformed)), /LEGACY_COORDINATES_INVALID/);
  }
});

test('public display drift is compared by hash without retaining names and --live only selects loopback 8080', async () => {
  const reference = await capture(ids, transport(false));
  await assert.rejects(() => verifyReference(reference, async url => {
    const p = payload(url.split('/').at(-1)); p.data.publicSubjects[0].displayName = 'DIFFERENT_NAME'; return json(p);
  }), /PUBLIC_CONTRACT_MISMATCH/);
  const calls = [];
  await verifyReference(reference, transport(true, calls), true);
  assert.ok(calls.every(call => call.url.startsWith('http://127.0.0.1:8080/')));
  assert.equal(parseArguments(['verify', '--reference-file', '/private/reference.json', '--live']).live, true);
  assert.throws(() => parseArguments(['capture', '--ids-file', '/private/ids.json', '--reference-file', '/private/ref.json', '--live']));
});

test('HTTP paths cannot expand, repeat, redirect or return unbounded JSON', async () => {
  const client = makeClient('capture', ids, transport(false));
  await assert.rejects(() => client(`/api/public/credentials/${ids[0]}/package`), /HTTP_SCOPE_REFUSED/);
  await assert.rejects(() => client('http://example.invalid/'), /HTTP_SCOPE_REFUSED/);
  await client(`/api/public/credentials/${ids[0]}`);
  await assert.rejects(() => client(`/api/public/credentials/${ids[0]}`), /HTTP_SCOPE_REFUSED/);
  await assert.rejects(() => makeClient('capture', ids, async () => new Response('', { status: 302 }))
    (`/api/public/credentials/${ids[0]}`), /HTTP_STATUS_NOT_200/);
  await assert.rejects(() => makeClient('capture', ids, async () => new Response('x'.repeat(262145)))
    (`/api/public/credentials/${ids[0]}`), /HTTP_BODY_TOO_LARGE/);
});

test('invalid PDF/ZIP cannot count as a compatibility pass', async () => {
  const reference = await capture(ids, transport(false));
  await assert.rejects(() => verifyReference(reference, async url => url.endsWith('/certificate')
    ? new Response('<html>error</html>') : json(payload(url.split('/').at(-1)))), /BINARY_MAGIC_INVALID/);
});

test('binary streaming has an enforced bound and transport failures never expose URLs or secrets', async () => {
  await assert.rejects(() => makeClient('verify', ids, async () => new Response(new Uint8Array(8 * 1024 * 1024 + 1)))
    (`/api/public/credentials/${ids[0]}/package`), /HTTP_BODY_TOO_LARGE/);
  await assert.rejects(() => makeClient('verify', ids, async () => {
    throw new Error('synthetic token=must-not-escape https://private.invalid/');
  })(`/api/public/credentials/${ids[0]}`), error => error.message === 'HTTP_REQUEST_FAILED');
});

test('timeout aborts stalled transport and fails without retry', async context => {
  context.mock.timers.enable({ apis: ['setTimeout'] });
  let requests = 0;
  const client = makeClient('capture', ids, async (_url, options) => {
    requests += 1;
    return new Promise((_resolve, reject) => options.signal.addEventListener('abort',
      () => reject(new Error('synthetic timeout')), { once: true }));
  });
  const pending = assert.rejects(() => client(`/api/public/credentials/${ids[0]}`), /HTTP_REQUEST_FAILED/);
  context.mock.timers.tick(15000);
  await pending;
  assert.equal(requests, 1);
});

test('references and exact six UUID CLI reject secret fields, duplicates and custom origins', async () => {
  assert.throws(() => validateIds([...ids.slice(0, 5), ids[0]]));
  assert.throws(() => validateIds([...ids, ids[0]]));
  assert.throws(() => parseArguments(['verify', '--origin', 'https://example.invalid']));
  const reference = await capture(ids, transport(false)); reference.token = 'must-not-be-stored';
  assert.throws(() => validateReference(reference));
});

test('protected reference creation is exclusive and private input rejects weak permissions and symlinks', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'trekkey-legacy-gate-test-'));
  try {
    await chmod(directory, 0o700);
    const reference = await capture(ids, transport(false));
    const file = join(directory, 'reference.json');
    await writeReference(file, reference);
    assert.equal((await stat(file)).mode & 0o777, 0o600);
    assert.deepEqual(await privateJson(file), reference);
    await assert.rejects(() => writeReference(file, reference));
    const link = join(directory, 'link.json'); await symlink(file, link);
    await assert.rejects(() => privateJson(link));
    const unsafe = join(directory, 'unsafe.json'); await writeFile(unsafe, JSON.stringify(ids), { mode: 0o644 });
    await assert.rejects(() => privateJson(unsafe), /PRIVATE_FILE_REQUIRED/);
  } finally { await rm(directory, { recursive: true, force: true }); }
});
