import assert from 'node:assert/strict';
import { chmodSync, mkdtempSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import { Ed25519Keypair } from '@mysten/sui/keypairs/ed25519';
import { launchEnvironment, readMountedFile } from '../src/server-launch.js';

function setup() {
  const dir = mkdtempSync(join(tmpdir(), 'trekkey-launch-test-'));
  const signer = Ed25519Keypair.fromSecretKey(new Uint8Array(32).fill(7));
  const manifest = { network: 'testnet', chainIdentifier: 'aabbccdd', packageId: `0x${'11'.repeat(32)}`,
    registryId: `0x${'22'.repeat(32)}`, protocolVersion: 1, relayerAddress: signer.toSuiAddress() };
  const env = { SUI_MANIFEST_FILE: join(dir, 'deployment.json'), SUI_RELAYER_KEY_FILE: join(dir, 'relayer.key'),
    SUI_GATEWAY_TOKEN_FILE: join(dir, 'gateway-token'), SUI_GATEWAY_JOURNAL_DIR: join(dir, 'journal') };
  writeFileSync(env.SUI_MANIFEST_FILE, JSON.stringify(manifest), { mode: 0o600 });
  writeFileSync(env.SUI_RELAYER_KEY_FILE, signer.getSecretKey(), { mode: 0o600 });
  writeFileSync(env.SUI_GATEWAY_TOKEN_FILE, 'synthetic-test-token-'.repeat(3), { mode: 0o600 });
  return { dir, env, manifest };
}
test('launcher imports only matching testnet manifest and private file secrets without mutating input', () => {
  const f = setup();
  try {
    const output = launchEnvironment(f.env);
    assert.equal(output.SUI_PACKAGE_ID, f.manifest.packageId);
    assert.equal(output.SUI_NETWORK, 'testnet');
    assert.equal(output.SUI_GATEWAY_TOKEN, 'synthetic-test-token-'.repeat(3));
    assert.equal(Object.keys(f.env).length, 4);
  } finally { rmSync(f.dir, { recursive: true, force: true }); }
});
test('launcher rejects permissive secret mode, symbolic links and non-ASCII token', () => {
  const f = setup();
  try {
    chmodSync(f.env.SUI_RELAYER_KEY_FILE, 0o644);
    assert.throws(() => launchEnvironment(f.env));
    chmodSync(f.env.SUI_RELAYER_KEY_FILE, 0o600);
    symlinkSync(f.env.SUI_RELAYER_KEY_FILE, join(f.dir, 'link'));
    assert.throws(() => readMountedFile(join(f.dir, 'link'), true));
    writeFileSync(f.env.SUI_GATEWAY_TOKEN_FILE, '한'.repeat(40));
    assert.throws(() => launchEnvironment(f.env));
  } finally { rmSync(f.dir, { recursive: true, force: true }); }
});
test('launcher rejects network, environment and key/manifest identity mismatches before RPC', () => {
  const f = setup();
  try {
    assert.throws(() => launchEnvironment({ ...f.env, SUI_NETWORK: 'mainnet' }));
    assert.throws(() => launchEnvironment({ ...f.env, SUI_PACKAGE_ID: `0x${'33'.repeat(32)}` }));
    writeFileSync(f.env.SUI_MANIFEST_FILE, JSON.stringify({ ...f.manifest, relayerAddress: `0x${'33'.repeat(32)}` }));
    assert.throws(() => launchEnvironment(f.env));
    writeFileSync(f.env.SUI_MANIFEST_FILE, JSON.stringify({ ...f.manifest, network: 'mainnet' }));
    assert.throws(() => launchEnvironment(f.env));
  } finally { rmSync(f.dir, { recursive: true, force: true }); }
});
