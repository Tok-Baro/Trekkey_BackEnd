import assert from 'node:assert/strict';
import { mkdtemp, readFile, realpath, rm, stat, symlink } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import { evidenceSecret, parseArguments, publicEnvironment, secretRecords, validateRoot, writeExclusive } from './prepare-runtime.mjs';

// No prepareRuntime(), randomBytes(), real secrets, root operations, network or child processes.
test('mode/root parser refuses broad, noncanonical and injection-bearing roots', () => {
  assert.equal(parseArguments(['--init-secrets']).root, '/srv/trekkey-sui-testnet');
  assert.equal(parseArguments(['--add-evidence-secret']).mode, '--add-evidence-secret');
  assert.equal(parseArguments(['--public-env', '--root', '/srv/trekkey-sui-testnet-demo']).mode, '--public-env');
  for (const path of ['/', '/srv', '/srv/other-project', 'relative', '/srv/../srv/trekkey-sui-testnet', '/srv/trekkey-sui-testnet\nX=1']) {
    assert.throws(() => validateRoot(path));
  }
  assert.throws(() => parseArguments(['--init-secrets', '--overwrite']));
  assert.throws(() => parseArguments(['--public-env', '--root', '/srv/trekkey-sui-testnet', '--extra']));
});
test('evidence add-on is independent and leaves the original four-record contract unchanged', () => {
  let called = 0;
  const buffer = Buffer.alloc(32, 9);
  assert.equal(evidenceSecret(size => { called++; assert.equal(size, 32); return buffer; }), '09'.repeat(32));
  assert.equal(called, 1);
  assert(buffer.every(value => value === 0));
  let counter = 0;
  assert.equal(secretRecords(size => Buffer.alloc(size, ++counter)).length, 4);
});
test('independent synthetic entropy produces separate correctly owned secret records', () => {
  let counter = 0;
  const records = secretRecords(size => Buffer.alloc(size, ++counter));
  assert.equal(counter, 4);
  assert.equal(new Set(records.map(record => record.value)).size, 4);
  assert.deepEqual(records.map(record => record.uid), [10001, 10001, 10001, 0]);
  assert.match(records[0].value, /^[0-9a-f]{96}$/);
  assert.equal(Buffer.from(records[1].value, 'base64').length, 64);
});
function manifest() {
  return { network: 'testnet', synthetic: true, protocolVersion: 1, chainIdentifier: '00000000',
    genesisDigest: '1'.repeat(32), packageId: `0x${'11'.repeat(32)}`, registryId: `0x${'22'.repeat(32)}`,
    relayerAddress: `0x${'33'.repeat(32)}`, bytecodeSha256: 'ab'.repeat(32),
    transactions: { publish: '1'.repeat(32), registry: '1'.repeat(32), authorize: '1'.repeat(32) },
    accidentallyIncludedSecret: 'SHOULD_NEVER_APPEAR' };
}
test('public env is allowlisted and rejects wrong network, identity, incomplete deployment and newline values', () => {
  const value = manifest(), text = publicEnvironment('/srv/trekkey-sui-testnet', value);
  assert.equal(text.trim().split('\n').length, 4);
  assert(!text.includes('SHOULD_NEVER_APPEAR'));
  for (const changed of [{ network: 'mainnet' }, { chainIdentifier: 'aabbccdd' }, { registryId: value.registryId + '\nX=1' },
    { synthetic: false }, { transactions: {} }, { protocolVersion: 2 }]) {
    assert.throws(() => publicEnvironment('/srv/trekkey-sui-testnet', { ...value, ...changed }));
  }
});
test('exclusive fixture writes preserve existing contents and reject symlink targets', async t => {
  const dir = await mkdtemp(join(await realpath(tmpdir()), 'trekkey-runtime-fixture-'));
  t.after(() => rm(dir, { recursive: true, force: true }));
  const target = join(dir, 'synthetic-file');
  await writeExclusive(target, 'public synthetic fixture', process.getuid());
  assert.equal((await stat(target)).mode & 0o777, 0o600);
  await assert.rejects(writeExclusive(target, 'replacement', process.getuid()));
  assert.equal(await readFile(target, 'utf8'), 'public synthetic fixture');
  await symlink(target, join(dir, 'alias'));
  await assert.rejects(writeExclusive(join(dir, 'alias'), 'replacement', process.getuid()));
});
