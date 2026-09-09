import test from 'node:test';
import assert from 'node:assert/strict';
import { allowedRequest, makeReadbackClient, parseArguments, validateReferences, verifyPersistence } from './verify-full-persistence.mjs';

const id = number => `${String(number).padStart(8, '0')}-1111-4111-8111-111111111111`;
const fixture = { organizationPublicId: id(1), adminUserId: 1, studentUserId: 2, secondAdminUserId: 3,
  adminEmail: `sui-full-admin-${id(1)}@example.invalid`, studentEmail: `sui-full-student-${id(2)}@example.invalid`,
  secondAdminEmail: `sui-full-second-admin-${id(3)}@example.invalid` };
const http = { status: 'PASS', origin: 'http://127.0.0.1:18080', database: 'trekkey_sui_full', synthetic: true,
  profilePublicId: id(4), evidencePublicId: id(5), casePublicId: id(6), coursePublicId: id(7), evidenceDecision: 'VERIFIED' };
const digests = ['391PcEPC2irKkmga4ybFUArZMYvkzByyDsELTuDRxk6C', '4pJJ6px463hFXxvEKUD5MQ16jrWZVoACjkUtppoANej6', 'A'.repeat(32), 'B'.repeat(32)];
const result = { status: 'PASS', database: 'trekkey_sui_full', fixture,
  credentials: { PARTICIPATION: id(8), WORK: id(9), AWARD: id(10) },
  transactions: digests.map((transactionDigest, index) => ({ transactionDigest, status: 'CONFIRMED',
    operation: index === 3 ? 'REVOKE' : 'ANCHOR_BATCH', checkpoint: String(index + 100) })) };

test('only explicit fixed-root CLI and sign-in POST or allowlisted GET paths are accepted', () => {
  assert.equal(parseArguments(['--verify-after-restart']), '/srv/trekkey-sui-testnet');
  assert.throws(() => parseArguments(['--verify-after-restart', '--root', '/srv/production']));
  for (const [method, path] of [['POST', '/api/me/graduation/evaluations'], ['POST', '/api/me/evidence-submissions'],
    ['PATCH', `/api/me/graduation/courses/${id(7)}`], ['GET', 'http://example.com'], ['GET', '/api/admin/blockchain/batches']]) {
    assert.equal(allowedRequest(method, path), false);
  }
});

test('prior PASS, four confirmed references and the exact original two digests are mandatory', () => {
  assert.equal(validateReferences(fixture, http, result).size, 3);
  assert.throws(() => validateReferences(fixture, http, { ...result, status: 'FAIL' }), /COMPLETED_CHAIN_RESULT_REQUIRED/);
  assert.throws(() => validateReferences(fixture, { ...http, status: 'FAIL' }, result), /HTTP_REFERENCE_SCOPE_INVALID/);
  assert.throws(() => validateReferences(fixture, http, { ...result, transactions: result.transactions.slice(1) }), /FOUR_CONFIRMED_TRANSACTIONS_REQUIRED/);
  const changed = structuredClone(result); changed.transactions[0].transactionDigest = 'C'.repeat(32);
  assert.throws(() => validateReferences(fixture, http, changed), /ORIGINAL_ANCHORS_NOT_PRESERVED/);
});

function fakeRequest({ credits = 3, validUnit = true, provider = 'SUI' } = {}) {
  const calls = [];
  const request = async (step, method, path, token, body) => {
    calls.push({ method, path });
    assert.equal(allowedRequest(method, path), true);
    if (step === 'STUDENT_SIGNIN') {
      assert.equal(body.email, fixture.studentEmail); assert.equal(body.password, 'synthetic-password');
      return { accessToken: 'SYNTHETIC_MEMORY_ONLY_ACCESS_TOKEN', userSessionRes: { id: 2, email: fixture.studentEmail, role: 'PARTICIPANT' } };
    }
    if (step === 'PROFILE_PERSISTED') return { configured: true, profilePublicId: id(4), organization: { publicId: id(1) }, totalCredits: credits };
    if (step === 'EVIDENCE_PERSISTED') return { publicId: id(5), casePublicId: id(6), status: 'VERIFIED', caseStatus: 'VERIFIED', reviewCount: 2, achievedAssuranceLevel: 'L2' };
    if (step === 'COURSE_PERSISTED') return [{ publicId: id(7), category: 'MAJOR_REQUIRED', academicUnitPublicId: id(11) }];
    if (step === 'ACADEMIC_UNIT_REFERENCE') return [{ publicId: validUnit ? id(11) : id(12) }];
    const type = Object.keys(result.credentials).find(type => path.endsWith(result.credentials[type]));
    assert.ok(type); const index = ['PARTICIPATION', 'WORK', 'AWARD'].indexOf(type);
    return { credentialPublicId: result.credentials[type], credentialType: type, issuerPublicId: id(1),
      verificationStatus: type === 'PARTICIPATION' ? 'REVOKED' : 'VALID', evidence: { blockchain: {
        provider, network: 'testnet', approvalScheme: 'TREKKEY_SUI_APPROVAL_V1', transactionDigest: digests[index], checkpointSequenceNumber: index + 100 } } };
  };
  return { calls, request };
}

test('readback preserves IDs/state and makes exactly one login followed only by GET requests', async () => {
  const { calls, request } = fakeRequest(); const artifact = {};
  await verifyPersistence(fixture, http, result, 'synthetic-password', artifact, request);
  assert.equal(calls.filter(call => call.method === 'POST').length, 1);
  assert.ok(calls.slice(1).every(call => call.method === 'GET'));
  assert.equal(artifact.profile.credits, 3); assert.equal(artifact.course.mappedPersisted, true);
  assert.equal(artifact.mappingReference, 'CURRENT_ORGANIZATION_UNIT_LIST_NOT_ORIGINAL_ID_SNAPSHOT');
  assert.equal(artifact.credentials.length, 3); assert.equal(artifact.originalAnchorsPreserved, 2);
  assert.equal(JSON.stringify(artifact).includes('ACCESS_TOKEN'), false);
  assert.equal(JSON.stringify(artifact).includes('synthetic-password'), false);
});

test('wrong profile credits, lost unit mapping and wrong chain provider fail closed without repair', async () => {
  for (const [options, code] of [[{ credits: 130 }, 'PROFILE_PERSISTENCE_MISMATCH'],
    [{ validUnit: false }, 'COURSE_UNIT_OUTSIDE_CURRENT_ORGANIZATION'], [{ provider: 'KAIA' }, 'CREDENTIAL_PERSISTENCE_MISMATCH']]) {
    const { calls, request } = fakeRequest(options);
    await assert.rejects(verifyPersistence(fixture, http, result, 'synthetic-password', {}, request), new RegExp(code));
    assert.ok(calls.slice(1).every(call => call.method === 'GET'));
  }
});

test('client refuses other writes before transport and hides raw network errors', async () => {
  let calls = 0; const checks = [];
  const client = makeReadbackClient(checks, async (url, options) => {
    calls++; assert.equal(url, 'http://127.0.0.1:18080/api/me/graduation/profile');
    assert.equal(options.redirect, 'error'); throw new Error('PRIVATE_NETWORK_PAYLOAD');
  });
  await assert.rejects(client('FORBIDDEN', 'POST', '/api/me/graduation/evaluations', 'SECRET'), /READBACK_SCOPE_REFUSED/);
  assert.equal(calls, 0);
  await assert.rejects(client('PROFILE', 'GET', '/api/me/graduation/profile', 'SECRET'), /^Error: PROFILE_TRANSPORT_FAILED$/);
  assert.equal(JSON.stringify(checks).includes('SECRET'), false);
});
