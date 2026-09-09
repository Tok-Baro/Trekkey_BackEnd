import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { allowedPath, makeClient, ORIGIN, parseArguments, syntheticPdf, validateFixture, workflow } from './verify-full-http.mjs';

const uuid = '11111111-1111-4111-8111-111111111111';
const fixture = { organizationPublicId: uuid,
  adminEmail: `sui-full-admin-${uuid}@example.invalid`, adminUserId: 1,
  studentEmail: `sui-full-student-${uuid}@example.invalid`, studentUserId: 2,
  secondAdminEmail: `sui-full-second-admin-${uuid}@example.invalid`, secondAdminUserId: 3 };

test('explicit operator gate rejects production origins, arbitrary roots and ambiguous fixture identities', () => {
  assert.deepEqual(parseArguments(['--run', '--confirmed-isolated-full-database']), { root: '/srv/trekkey-sui-testnet' });
  for (const args of [[], ['--run'], ['--run', '--confirmed-isolated-full-database', '--origin', 'http://127.0.0.1:8080'],
    ['--run', '--confirmed-isolated-full-database', '--root', '/srv/production']]) assert.throws(() => parseArguments(args));
  assert.equal(validateFixture(fixture), fixture);
  assert.throws(() => validateFixture({ ...fixture, studentEmail: 'real@example.com' }));
  assert.throws(() => validateFixture({ ...fixture, secondAdminUserId: 1 }));
  for (const path of ['http://example.com', '//example.com', '/api/admin/graduation/policies/sync', '/api/me/graduation/../auth/signin',
    '/api/me/graduation/profile?target=production']) assert.equal(allowedPath(path), false);
});

test('client pins origin, rejects redirects and does not expose token/body/error text in artifacts', async () => {
  const checks = []; let calls = 0;
  const client = makeClient(checks, async (url, options) => {
    calls++; assert.equal(url, ORIGIN + '/api/me/graduation/profile');
    assert.equal(options.redirect, 'error'); assert.ok(options.signal);
    assert.equal(options.headers.Authorization, 'Bearer SECRET_TOKEN');
    return new Response(JSON.stringify({ message: 'SECRET_RESPONSE' }), { status: 500 });
  });
  await assert.rejects(client('PROFILE', 'GET', '/api/me/graduation/profile', 'SECRET_TOKEN'), /PROFILE_HTTP_500/);
  await assert.rejects(client('PROFILE', 'GET', 'http://example.com', 'SECRET_TOKEN'), /REQUEST_SCOPE_REFUSED/);
  assert.equal(calls, 1); assert.equal(JSON.stringify(checks).includes('SECRET'), false);
  const redirect = makeClient([], async () => { throw new Error('SECRET_NETWORK_ERROR'); });
  await assert.rejects(redirect('REDIRECT', 'GET', '/api/me/graduation/profile'), /^Error: REDIRECT_TRANSPORT_FAILED$/);
});

test('synthetic PDF xref and content are deterministic and explicitly non-credential', () => {
  const pdf = syntheticPdf(); const value = pdf.toString('ascii');
  assert.deepEqual(pdf, syntheticPdf()); assert.ok(value.includes('NOT AN ACADEMIC CREDENTIAL'));
  const start = Number(/startxref\n(\d+)/.exec(value)[1]); assert.equal(value.slice(start, start + 4), 'xref');
  const offsets = [...value.matchAll(/^(\d{10}) 00000 n /gm)].map(match => Number(match[1]));
  assert.equal(offsets.length, 5); offsets.forEach((offset, index) => assert.ok(value.slice(offset).startsWith(`${index + 1} 0 obj`)));
});

function fakeRequest({ duplicateChangesState = false } = {}) {
  const steps = []; const hash = createHash('sha256').update(syntheticPdf()).digest('hex');
  const request = async (step, method, path, token, body, expected) => {
    steps.push(step); assert.equal(allowedPath(path), true);
    if (step.startsWith('SIGNIN_')) {
      const role = ['admin', 'student', 'secondAdmin'].find(role => fixture[`${role}Email`] === body.email);
      assert.ok(role); assert.equal(body.password, 'synthetic-password');
      return { accessToken: `MEMORY_ONLY_TEST_TOKEN_${role}`, userSessionRes: { id: fixture[`${role}UserId`],
        email: fixture[`${role}Email`], role: role === 'student' ? 'PARTICIPANT' : 'ADMIN' } };
    }
    if (step === 'DUPLICATE_ADMIN_REVIEW_DENIED') return { code: 'EVIDENCE_REVIEWER_CONFLICT' };
    if (step.endsWith('_DENIED') || step === 'UNAUTHENTICATED_PROFILE') return { code: 'EXPECTED_NEGATIVE' };
    if (step === 'PROFILE_INITIAL') return { configured: false, organization: { publicId: uuid } };
    if (step === 'PROFILE_CREATE' || step === 'PROFILE_READBACK') return { configured: true, profilePublicId: uuid,
      organization: { publicId: uuid }, totalCredits: 130 };
    if (step === 'EVIDENCE_INITIAL' || step === 'COURSES_INITIAL') return [];
    if (step.startsWith('EVALUATE_')) { assert.equal(expected, 201); return {
      status: 'INDETERMINATE', evaluationPublicId: uuid, policies: [{}], requirements: [] }; }
    if (step === 'EVIDENCE_MULTIPART_CREATE') {
      assert.ok(body instanceof FormData); assert.ok((await body.get('files').text()).includes('SYNTHETIC'));
      return { publicId: uuid, casePublicId: uuid, files: [{ sha256: hash }] };
    }
    if (step === 'ADMIN_FIRST_REVIEW') return { caseStatus: 'AWAITING_SECOND_REVIEW', reviewCount: 1, finalDecision: null };
    if (step === 'DUPLICATE_REVIEW_READBACK') return { caseStatus: 'AWAITING_SECOND_REVIEW', reviewCount: duplicateChangesState ? 2 : 1 };
    if (step === 'SECOND_ADMIN_REVIEW') return { caseStatus: 'VERIFIED', finalDecision: 'VERIFIED', reviewCount: 2 };
    if (step === 'VERIFIED_EVIDENCE_READBACK') return { status: 'VERIFIED', caseStatus: 'VERIFIED', reviewCount: 2, achievedAssuranceLevel: 'L2' };
    if (step === 'ACADEMIC_UNITS_LIST') return [{ publicId: uuid, unitType: 'TRACK' }];
    if (step === 'COURSE_CSV_IMPORT') return { applied: true, courseCount: 1 };
    if (step === 'COURSES_AFTER_IMPORT') return [{ publicId: uuid, courseCode: 'SYNHTTP001' }];
    if (step === 'COURSE_PATCH_WITH_UNIT') return { category: 'MAJOR_REQUIRED', academicUnitPublicId: uuid };
    if (step === 'COURSE_MAPPING_READBACK') return [{ category: 'MAJOR_REQUIRED', academicUnitPublicId: uuid }];
    throw new Error(`UNEXPECTED_${step}`);
  };
  return { request, steps };
}
test('synthetic offline workflow checks three logins, duplicate-review state, four evaluations and course mapping', async () => {
  const { request, steps } = fakeRequest(); const artifact = { evaluations: [] };
  await workflow(fixture, 'synthetic-password', artifact, request);
  assert.equal(steps.filter(step => step.startsWith('SIGNIN_')).length, 3);
  assert.equal(artifact.evaluations.length, 4); assert.equal(artifact.evidenceDecision, 'VERIFIED');
  assert.ok(steps.includes('COURSE_PATCH_WITH_UNIT')); assert.equal(JSON.stringify(artifact).includes('MEMORY_ONLY'), false);
  assert.equal(JSON.stringify(artifact).includes('synthetic-password'), false);
});
test('workflow refuses duplicate reviewer side effects before second approval', async () => {
  const { request, steps } = fakeRequest({ duplicateChangesState: true });
  await assert.rejects(workflow(fixture, 'synthetic-password', { evaluations: [] }, request), /DUPLICATE_REVIEW_CHANGED_STATE/);
  assert.equal(steps.includes('SECOND_ADMIN_REVIEW'), false);
});
