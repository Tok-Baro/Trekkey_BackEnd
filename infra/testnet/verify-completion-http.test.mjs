import test from 'node:test';
import assert from 'node:assert/strict';
import { chmod, link, mkdir, mkdtemp, readFile, realpath, rm, symlink } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { writeExclusive } from './prepare-runtime.mjs';
import { directory, privateText } from './verify-full-persistence.mjs';
import { allowedRequest, CLAIM_FIELDS, CLAIM_NAME, claimCompletion, denialPaths, JUDGE_API, MANUAL_API,
  makeCompletionClient, parseArguments, RESULT_NAME, validateEvaluation, validatePublicClaims,
  validateSwagger, verifyCompletion } from './verify-completion-http.mjs';

// Synthetic fixtures only: no run(), root operations, real login, network, database or chain calls.
const id = number => `${String(number).padStart(8, '0')}-1111-4111-8111-111111111111`;
const PASSWORD = 'ab'.repeat(32), TOKEN = 'SYNTHETIC_MEMORY_ONLY_ACCESS_TOKEN';
const fixture = { organizationPublicId: id(1), adminUserId: 1, studentUserId: 2, secondAdminUserId: 3,
  adminEmail: `sui-full-admin-${id(1)}@example.invalid`, studentEmail: `sui-full-student-${id(2)}@example.invalid`,
  secondAdminEmail: `sui-full-second-admin-${id(3)}@example.invalid`, contestPublicId: id(20), teamPublicId: id(21) };
const http = { status: 'PASS', origin: 'http://127.0.0.1:18080', database: 'trekkey_sui_full', synthetic: true,
  profilePublicId: id(4), evidencePublicId: id(5), casePublicId: id(6), coursePublicId: id(7), evidenceDecision: 'VERIFIED' };
const digests = ['391PcEPC2irKkmga4ybFUArZMYvkzByyDsELTuDRxk6C', '4pJJ6px463hFXxvEKUD5MQ16jrWZVoACjkUtppoANej6', 'A'.repeat(32), 'B'.repeat(32)];
const types = ['PARTICIPATION', 'WORK', 'AWARD'];
const result = { status: 'PASS', database: 'trekkey_sui_full', fixture,
  credentials: { PARTICIPATION: id(8), WORK: id(9), AWARD: id(10) },
  transactions: digests.map((transactionDigest, index) => ({ transactionDigest, status: 'CONFIRMED',
    operation: index === 3 ? 'REVOKE' : 'ANCHOR_BATCH', checkpoint: String(index + 100) })) };
const operation = security => ({ responses: { 200: { description: 'Synthetic response' } }, security });
function swagger() {
  const jwt = [{ 'JWT Authentication': [] }];
  const paths = {
    [MANUAL_API]: { post: operation(jwt) }, [JUDGE_API]: { patch: operation(jwt) },
    '/api/public/credentials/{credentialPublicId}': { get: operation([]) },
    '/api/public/credentials/{credentialPublicId}/package': { get: operation([]) },
    '/api/auth/signin': { post: operation([]) }
  };
  for (let index = 0; index < 32; index++) paths[`/api/admin/synthetic/${index}`] = { get: operation(jwt) };
  for (let index = 0; index < 71; index++) paths[`/api/me/synthetic/${index}`] = { get: operation(jwt) };
  return { openapi: '3.1.0', paths,
    components: { securitySchemes: { 'JWT Authentication': { type: 'http', scheme: 'bearer', bearerFormat: 'JWT' } } } };
}
function credential(type) {
  const index = types.indexOf(type), publicId = result.credentials[type];
  return { credentialPublicId: publicId, credentialType: type, issuerPublicId: id(1),
    verificationStatus: type === 'PARTICIPATION' ? 'REVOKED' : 'VALID',
    publicSubjects: [{ subjectRef: `public-subject:${publicId}:0`, displayName: 'SYNTHETIC_DISPLAY_NAME_NOT_RECORDED' }],
    evidence: { ...Object.fromEntries(CLAIM_FIELDS.map(key => [key, true])), blockchain: {
      provider: 'SUI', network: 'testnet', approvalScheme: 'TREKKEY_SUI_APPROVAL_V1',
      transactionDigest: digests[index], checkpointSequenceNumber: index + 100 } } };
}
function evaluation() {
  return { evaluationPublicId: id(30), status: 'NOT_ELIGIBLE', coverage: {
    complete: false, gaps: [{ code: 'UNIT_SELECTION_MISSING', message: 'Synthetic source text not recorded' }] } };
}
function fakeTransport(mutate = (_path, value) => value) {
  const calls = [];
  const transport = async (url, options) => {
    const target = new URL(url), path = target.pathname;
    assert.equal(target.origin, http.origin); assert.equal(target.search, ''); assert.equal(target.hash, '');
    assert.equal(options.redirect, 'error'); assert.ok(options.signal instanceof AbortSignal);
    calls.push({ method: options.method, path, body: options.body, token: options.headers.Authorization });
    let data;
    if (path === '/api/auth/signin') {
      assert.deepEqual(JSON.parse(options.body), { email: fixture.studentEmail, password: PASSWORD });
      data = { accessToken: TOKEN, userSessionRes: { id: 2, email: fixture.studentEmail, role: 'PARTICIPANT' } };
    } else if (path === '/api/me/graduation/profile') {
      data = { configured: true, profilePublicId: id(4), organization: { publicId: id(1) }, totalCredits: 3 };
    } else if (path === `/api/me/evidence-submissions/${id(5)}`) {
      data = { publicId: id(5), casePublicId: id(6), status: 'VERIFIED', caseStatus: 'VERIFIED', reviewCount: 2, achievedAssuranceLevel: 'L2' };
    } else if (path === '/api/me/graduation/courses') {
      data = [{ publicId: id(7), category: 'MAJOR_REQUIRED', academicUnitPublicId: id(11) }];
    } else if (path === '/api/me/graduation/academic-units') data = [{ publicId: id(11) }];
    else if (path === '/v3/api-docs') return response(mutate(path, swagger()), 200);
    else if (path === '/api/me/graduation/evaluations') {
      assert.equal(options.body, '{}'); assert.equal(options.headers.Authorization, `Bearer ${TOKEN}`);
      return response({ isSuccess: true, data: mutate(path, evaluation()) }, 201);
    } else if (path === denialPaths(fixture).submission || path === denialPaths(fixture).judge) {
      assert.equal(options.body, undefined); assert.equal(options.headers.Authorization, `Bearer ${TOKEN}`);
      return response({ detail: 'FORBIDDEN_RAW_BODY_MUST_NOT_BE_RECORDED' }, mutate(path, 403));
    } else {
      const type = types.find(item => path === `/api/public/credentials/${result.credentials[item]}`);
      assert.ok(type, 'Only fixed known credential readbacks allowed'); data = credential(type);
    }
    return response({ isSuccess: true, data: mutate(path, data) }, 200);
  };
  return { calls, transport };
}
function response(value, status) {
  return new Response(JSON.stringify(value), { status, headers: { 'content-type': 'application/json' } });
}

test('completion CLI and request scope cannot switch root/origin or write arbitrary resources', () => {
  assert.equal(parseArguments(['--verify-completion-v7']), '/srv/trekkey-sui-testnet');
  for (const args of [[], ['--verify-after-restart'], ['--verify-completion-v7', '--root', '/srv/production'],
    ['--verify-completion-v7', '--force']]) assert.throws(() => parseArguments(args));
  for (const [method, path] of [['POST', '/api/me/evidence-submissions'], ['PUT', '/api/me/graduation/profile'],
    ['POST', '/api/admin/blockchain/batches'], ['DELETE', denialPaths(fixture).judge],
    ['POST', '/api/me/graduation/evaluations?policyAsOf=2026-01-01'], ['GET', 'https://example.com/v3/api-docs'],
    ['GET', '/v3/api-docs?group=production']]) assert.equal(allowedRequest(method, path, fixture), false);
  assert.throws(() => denialPaths({ ...fixture, teamPublicId: '../other' }), /DENIAL_FIXTURE_SCOPE_INVALID/);
});

test('full workflow preserves eight readbacks and performs exactly one login, one evaluation and two empty 403 probes', async () => {
  const { calls, transport } = fakeTransport(), artifact = { checks: [] };
  await verifyCompletion(fixture, http, result, PASSWORD, artifact, makeCompletionClient(artifact.checks, fixture, transport));
  assert.equal(artifact.checks.length, 12); assert.equal(artifact.persistenceChecks, 8);
  assert.equal(artifact.publicClaims.length, 3); assert.equal(artifact.swagger.operationCount, 108);
  assert.equal(artifact.evaluation.coverageComplete, false); assert.equal(artifact.evaluation.coverageGapCount, 1);
  assert.equal(artifact.originalAnchorsPreserved, 2); assert.equal(artifact.confirmedTransactionReferences, 4);
  assert.equal(calls.filter(call => call.path === '/api/auth/signin').length, 1);
  assert.equal(calls.filter(call => call.path === '/api/me/graduation/evaluations').length, 1);
  assert.equal(calls.filter(call => call.method !== 'GET').length, 4);
  const serialized = JSON.stringify(artifact);
  for (const privateValue of [TOKEN, PASSWORD, fixture.studentEmail, 'SYNTHETIC_DISPLAY_NAME', 'FORBIDDEN_RAW_BODY',
    'Synthetic source text']) assert.equal(serialized.includes(privateValue), false);
});

test('every integrity claim is strict true and subject refs must use exact credential-local public sequence', () => {
  for (const field of CLAIM_FIELDS) for (const invalid of [false, null, undefined, 'true', 1]) {
    const value = credential('WORK'); value.evidence[field] = invalid;
    assert.throws(() => validatePublicClaims(value), /PUBLIC_SIX_CLAIMS_NOT_TRUE/);
  }
  for (const ref of ['user:2', `public-subject:${id(99)}:0`, `public-subject:${id(9)}:1`, `public-subject:${id(9)}:00`]) {
    const value = credential('WORK'); value.publicSubjects[0].subjectRef = ref;
    assert.throws(() => validatePublicClaims(value), /PUBLIC_SUBJECT_ALIAS_INVALID/);
  }
  assert.throws(() => validatePublicClaims({ ...credential('WORK'), publicSubjects: [] }), /PUBLIC_SUBJECTS_REQUIRED/);
  const value = credential('WORK'); value.publicSubjects.push({ subjectRef: `public-subject:${id(9)}:1` });
  assert.equal(validatePublicClaims(value).publicSubjectCount, 2);
});

test('changed public claims fail before evaluation or admin probes without repairing any state', async () => {
  const { calls, transport } = fakeTransport((path, value) => {
    if (path.endsWith(id(9))) value.evidence.credentialClaimsMatch = false;
    return value;
  });
  const artifact = { checks: [] };
  await assert.rejects(verifyCompletion(fixture, http, result, PASSWORD, artifact,
    makeCompletionClient(artifact.checks, fixture, transport)), /PUBLIC_SIX_CLAIMS_NOT_TRUE/);
  assert.equal(calls.filter(call => call.method !== 'GET').length, 1);
});

test('Swagger validation catches count, missing APIs, global-only public security and anonymous admin alternatives', () => {
  assert.equal(validateSwagger(swagger()).newAdminApis, 2);
  for (const mutate of [
    api => { delete api.paths['/api/me/synthetic/0']; },
    api => { api.paths['/api/me/replacement'] = api.paths[MANUAL_API]; delete api.paths[MANUAL_API]; },
    api => { delete api.paths['/api/public/credentials/{credentialPublicId}'].get.security; },
    api => { api.paths['/api/public/credentials/{credentialPublicId}'].get.security = [{ 'JWT Authentication': [] }]; },
    api => { api.paths[JUDGE_API].patch.security = []; },
    api => { api.paths[JUDGE_API].patch.security = [{}, { 'JWT Authentication': [] }]; },
    api => { api.components.securitySchemes['JWT Authentication'].scheme = 'basic'; }
  ]) { const api = swagger(); mutate(api); assert.throws(() => validateSwagger(api)); }
});

test('graduation coverage rejects false eligible, missing/empty coverage and unknown statuses', () => {
  assert.equal(validateEvaluation(evaluation()).overallEligibilityNotAsserted, true);
  for (const changed of [{ status: 'ELIGIBLE' }, { status: 'UNKNOWN' }, { coverage: null },
    { coverage: { complete: true, gaps: [{ code: 'GAP' }] } }, { coverage: { complete: false, gaps: [] } },
    { coverage: { complete: false, gaps: [{ code: 'private detail with spaces' }] } }]) {
    assert.throws(() => validateEvaluation({ ...evaluation(), ...changed }));
  }
});

test('client refuses repeated/uncertain writes, changed login identity and any nonempty admin probe', async () => {
  let calls = 0;
  const client = makeCompletionClient([], fixture, async () => { calls++; throw new Error('RAW_PRIVATE_NETWORK_DETAILS'); });
  await assert.rejects(client('LOGIN', 'POST', '/api/auth/signin', null,
    { email: fixture.adminEmail, password: PASSWORD }), /COMPLETION_STUDENT_LOGIN_ONLY/);
  await assert.rejects(client('DENIED', 'POST', denialPaths(fixture).submission, TOKEN, {}), /COMPLETION_ADMIN_PROBE_BODY_REFUSED/);
  await assert.rejects(client('EVALUATION', 'POST', '/api/me/graduation/evaluations', TOKEN, { policyAsOf: '2026-01-01' }),
    /COMPLETION_EMPTY_EVALUATION_ONLY/);
  assert.equal(calls, 0);
  await assert.rejects(client('LOGIN', 'POST', '/api/auth/signin', null,
    { email: fixture.studentEmail, password: PASSWORD }), /^Error: LOGIN_TRANSPORT_FAILED$/);
  await assert.rejects(client('LOGIN_RETRY', 'POST', '/api/auth/signin', null,
    { email: fixture.studentEmail, password: PASSWORD }), /COMPLETION_WRITE_ALREADY_ATTEMPTED/);
  assert.equal(calls, 1);
});

test('a 400 or 200 admin response is not accepted as authorization protection and stops before evaluation', async () => {
  for (const status of [200, 400]) {
    const { calls, transport } = fakeTransport((path, value) => path === denialPaths(fixture).submission ? status : value);
    const artifact = { checks: [] };
    await assert.rejects(verifyCompletion(fixture, http, result, PASSWORD, artifact,
      makeCompletionClient(artifact.checks, fixture, transport)), new RegExp(`STUDENT_MANUAL_SUBMISSION_DENIED_HTTP_${status}`));
    assert.equal(calls.some(call => call.path === '/api/me/graduation/evaluations'), false);
  }
});

test('public docs client refuses redirects and malformed/oversized bodies without retaining raw response details', async () => {
  const checks = [];
  const client = makeCompletionClient(checks, fixture, async () => new Response('PRIVATE RESPONSE CONTENT', { status: 302 }));
  await assert.rejects(client('DOCS', 'GET', '/v3/api-docs'), /^Error: DOCS_HTTP_302$/);
  const malformed = makeCompletionClient([], fixture, async () => new Response('PRIVATE RESPONSE CONTENT', { status: 200 }));
  await assert.rejects(malformed('DOCS', 'GET', '/v3/api-docs'), /^Error: DOCS_INVALID_JSON$/);
  const oversized = makeCompletionClient([], fixture, async () => new Response('x'.repeat(2097153), { status: 200 }));
  await assert.rejects(oversized('DOCS', 'GET', '/v3/api-docs'), /^Error: DOCS_RESPONSE_TOO_LARGE$/);
  assert.equal(JSON.stringify(checks).includes('PRIVATE'), false);
});

test('protected reference readers and exclusive completion claims reject symlinks, hardlinks, lax modes and existing outputs', async t => {
  const root = await mkdtemp(join(await realpath(tmpdir()), 'trekkey-completion-fixture-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  const uid = process.getuid(), source = join(root, 'synthetic-reference');
  await chmod(root, 0o700); await directory(root, [uid], 0o700);
  await writeExclusive(source, 'synthetic reference', uid);
  assert.equal(await privateText(source, [uid], 64), 'synthetic reference');
  await chmod(source, 0o644); await assert.rejects(privateText(source, [uid], 64), /READBACK_FILE_UNTRUSTED/);
  await chmod(source, 0o600); await link(source, join(root, 'hardlink'));
  await assert.rejects(privateText(source, [uid], 64), /READBACK_FILE_UNTRUSTED/);
  await symlink(source, join(root, 'symlink')); await assert.rejects(privateText(join(root, 'symlink'), [uid], 64));
  const output = join(root, 'output'); await mkdir(output, { mode: 0o700 });
  await claimCompletion(output, '2026-09-08T00:00:00.000Z', uid);
  const original = await readFile(join(output, CLAIM_NAME), 'utf8');
  await assert.rejects(claimCompletion(output, '2026-09-09T00:00:00.000Z', uid));
  assert.equal(await readFile(join(output, CLAIM_NAME), 'utf8'), original);
  const orphan = join(root, 'orphan'); await mkdir(orphan, { mode: 0o700 });
  await writeExclusive(join(orphan, RESULT_NAME), 'preserve existing result', uid);
  await assert.rejects(claimCompletion(orphan, '2026-09-08T00:00:00.000Z', uid), /COMPLETION_RESULT_ALREADY_EXISTS/);
  assert.equal(await readFile(join(orphan, RESULT_NAME), 'utf8'), 'preserve existing result');
});

test('wrong historical database or fixture identity is rejected before the first sign-in', async () => {
  const { calls, transport } = fakeTransport(), artifact = { checks: [] };
  await assert.rejects(verifyCompletion(fixture, { ...http, database: 'trekkey' }, result, PASSWORD, artifact,
    makeCompletionClient(artifact.checks, fixture, transport)), /HTTP_REFERENCE_SCOPE_INVALID/);
  await assert.rejects(verifyCompletion(fixture, http, { ...result, fixture: { ...fixture, teamPublicId: id(99) } }, PASSWORD, artifact,
    makeCompletionClient(artifact.checks, fixture, transport)), /COMPLETION_FIXTURE_IDENTITY_MISMATCH/);
  assert.equal(calls.length, 0);
});
