import assert from 'node:assert/strict';
import test from 'node:test';
import { approvalDigest, approvalStructHash, canonicalIssuerSignature, recoverIssuer, signIssuer } from '../src/approval.js';
import { bytes, hexBytes, parseBatch, parseStatus, SCHEME } from '../src/contracts.js';
import { signApprovalPayload } from '../src/cli.js';
import { fixture, h, identity } from './fixtures.js';

for (const kind of ['batch', 'status'] as const) {
  test(`${kind}: same approval bytes, keccak digest, low-s signature, recovered issuer as independent Move/Java fixture`, () => {
    const f = fixture[kind], a = kind === 'batch' ? parseBatch(f.approval) : parseStatus(f.approval);
    assert.equal(hexBytes(approvalStructHash(a)), f.structHash);
    assert.equal(hexBytes(approvalDigest(identity, a)), f.digest);
    assert.equal(recoverIssuer(identity, a, canonicalIssuerSignature(f.signature)), fixture.signer);
    assert.equal(signIssuer(identity, a, bytes(`0x${'00'.repeat(31)}01`)), f.signature);
  });
}
test('approval replay across chain, package, or registry changes the digest', () => {
  const a = parseBatch(fixture.batch.approval), original = hexBytes(approvalDigest(identity, a));
  for (const change of [{ chainIdentifier: '00000001' }, { packageId: h(5) }, { registryId: h(6) }]) {
    assert.notEqual(hexBytes(approvalDigest({ ...identity, ...change }, a)), original);
  }
});
test('invalid signature length, recovery, zero-r and high-s are rejected; v0/1 normalized', () => {
  assert.throws(() => canonicalIssuerSignature('0x11'));
  assert.throws(() => canonicalIssuerSignature(`0x${'01'.repeat(64)}02`));
  assert.throws(() => canonicalIssuerSignature(`0x${'00'.repeat(32)}${'01'.repeat(32)}1b`));
  assert.throws(() => canonicalIssuerSignature(`0x${'01'.repeat(32)}${'ff'.repeat(32)}1b`));
  const raw = canonicalIssuerSignature(fixture.batch.signature);
  raw[64] = raw[64]! - 27;
  assert.equal(hexBytes(canonicalIssuerSignature(hexBytes(raw))), fixture.batch.signature);
});
test('integer parsing rejects floating/unsafe/overflow/negative and wrong tree/action semantics', () => {
  for (const leafCount of [-1, 1.5, '4294967296', '03', 9007199254740992]) assert.throws(() => parseBatch({ ...fixture.batch.approval, leafCount }));
  assert.throws(() => parseBatch({ ...fixture.batch.approval, approvalNonce: '18446744073709551616' }));
  assert.throws(() => parseBatch({ ...fixture.batch.approval, treeVersion: '2' }));
  assert.throws(() => parseStatus({ ...fixture.status.approval, action: '2' }));
  assert.throws(() => parseStatus({ ...fixture.status.approval, action: '0' }));
});
test('offline signing CLI verifies copied digest, independent manifest and expected school signer', () => {
  const payload = { scheme: SCHEME, signatureScheme: 'secp256k1-recoverable-low-s', domain: fixture.domain,
    primaryType: 'BatchApproval', message: fixture.batch.approval, digestHex: fixture.batch.digest };
  const secret = bytes(`0x${'00'.repeat(31)}01`);
  assert.equal(signApprovalPayload(payload, identity, secret, fixture.signer).signature, fixture.batch.signature);
  assert.throws(() => signApprovalPayload({ ...payload, digestHex: h(1) }, identity, secret, fixture.signer));
  assert.throws(() => signApprovalPayload(payload, { ...identity, registryId: h(1) }, secret, fixture.signer));
  assert.throws(() => signApprovalPayload(payload, identity, secret, `0x${'22'.repeat(20)}`));
});
