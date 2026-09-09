#!/usr/bin/env node
// Post-restart readback only. The sole HTTP mutation is one synthetic student's sign-in/refresh session.
import { constants } from 'node:fs';
import { lstat, open, realpath } from 'node:fs/promises';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { DEFAULT_ROOT, writeExclusive } from './prepare-runtime.mjs';
import { ORIGIN, validateFixture } from './verify-full-http.mjs';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const DIGEST = /^[1-9A-HJ-NP-Za-km-z]{32,44}$/;
const ORIGINAL_DIGESTS = new Set(['391PcEPC2irKkmga4ybFUArZMYvkzByyDsELTuDRxk6C', '4pJJ6px463hFXxvEKUD5MQ16jrWZVoACjkUtppoANej6']);
const TYPES = ['PARTICIPATION', 'WORK', 'AWARD'];
const ensure = (condition, code) => { if (!condition) throw new Error(code); };
const publicId = value => { ensure(typeof value === 'string' && UUID.test(value), 'REFERENCE_ID_INVALID'); return value; };

export function parseArguments(args) {
  ensure(args.length === 1 && args[0] === '--verify-after-restart', 'EXPLICIT_READBACK_REQUIRED');
  return DEFAULT_ROOT; // No alternate origin, root or database flags.
}
export function allowedRequest(method, path) {
  if (method === 'POST') return path === '/api/auth/signin';
  return method === 'GET' && /^\/api\/(?:me\/graduation\/(?:profile|courses|academic-units)|me\/evidence-submissions\/[0-9a-f-]{36}|public\/credentials\/[0-9a-f-]{36})$/.test(path);
}
export function makeReadbackClient(checks, transport = fetch) {
  return async (step, method, path, token, body) => {
    ensure(/^[A-Z0-9_]+$/.test(step) && allowedRequest(method, path), 'READBACK_SCOPE_REFUSED');
    ensure(method === 'POST' || body === undefined, 'READBACK_GET_BODY_REFUSED');
    const headers = { Accept: 'application/json' };
    if (token) headers.Authorization = `Bearer ${token}`;
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    let response;
    try { response = await transport(ORIGIN + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body),
      redirect: 'error', signal: AbortSignal.timeout(20000) }); }
    catch { throw new Error(`${step}_TRANSPORT_FAILED`); }
    checks.push({ step, httpStatus: response.status, passed: response.status === 200 });
    if (response.status !== 200) { await response.body?.cancel(); throw new Error(`${step}_HTTP_${response.status}`); }
    const reader = response.body?.getReader(); ensure(reader, `${step}_EMPTY_RESPONSE`);
    const chunks = []; let size = 0;
    for (;;) {
      const next = await reader.read(); if (next.done) break;
      size += next.value.length;
      if (size > 1048576) { await reader.cancel(); throw new Error(`${step}_RESPONSE_TOO_LARGE`); }
      chunks.push(next.value);
    }
    let json;
    try { json = JSON.parse(Buffer.concat(chunks).toString('utf8')); }
    catch { throw new Error(`${step}_INVALID_JSON`); }
    ensure(json && Object.hasOwn(json, 'data'), `${step}_MISSING_DATA`);
    return json.data;
  };
}

export function validateReferences(fixture, http, result) {
  validateFixture(fixture);
  ensure(http && http.status === 'PASS' && http.origin === ORIGIN && http.database === 'trekkey_sui_full' && http.synthetic === true,
    'HTTP_REFERENCE_SCOPE_INVALID');
  for (const field of ['profilePublicId', 'evidencePublicId', 'casePublicId', 'coursePublicId']) publicId(http[field]);
  ensure(http.evidenceDecision === 'VERIFIED', 'VERIFIED_EVIDENCE_REFERENCE_REQUIRED');
  ensure(result?.status === 'PASS' && result.database === 'trekkey_sui_full', 'COMPLETED_CHAIN_RESULT_REQUIRED');
  for (const field of ['organizationPublicId', 'adminUserId', 'studentUserId', 'secondAdminUserId']) {
    ensure(result.fixture?.[field] === fixture[field], 'RESULT_FIXTURE_IDENTITY_MISMATCH');
  }
  for (const type of TYPES) publicId(result.credentials?.[type]);
  ensure(new Set(TYPES.map(type => result.credentials[type])).size === 3, 'CREDENTIAL_IDENTITIES_NOT_DISTINCT');
  ensure(Array.isArray(result.transactions) && result.transactions.length === 4, 'FOUR_CONFIRMED_TRANSACTIONS_REQUIRED');
  const anchors = new Map(); let revokes = 0;
  for (const tx of result.transactions) {
    ensure(tx.status === 'CONFIRMED' && DIGEST.test(tx.transactionDigest ?? '') && /^\d+$/.test(tx.checkpoint ?? ''),
      'CONFIRMED_TRANSACTION_REFERENCE_INVALID');
    if (tx.operation === 'ANCHOR_BATCH') anchors.set(tx.transactionDigest, tx.checkpoint);
    else { ensure(tx.operation === 'REVOKE', 'UNEXPECTED_TRANSACTION_OPERATION'); revokes++; }
  }
  ensure(anchors.size === 3 && revokes === 1 && [...ORIGINAL_DIGESTS].every(digest => anchors.has(digest)), 'ORIGINAL_ANCHORS_NOT_PRESERVED');
  return anchors;
}

export async function verifyPersistence(fixture, http, result, password, artifact, request) {
  const anchors = validateReferences(fixture, http, result);
  let token;
  try {
    const login = await request('STUDENT_SIGNIN', 'POST', '/api/auth/signin', null, { email: fixture.studentEmail, password });
    ensure(login.userSessionRes?.id === fixture.studentUserId && login.userSessionRes.email === fixture.studentEmail
      && login.userSessionRes.role === 'PARTICIPANT', 'STUDENT_SIGNIN_IDENTITY_MISMATCH');
    ensure(typeof login.accessToken === 'string' && login.accessToken.length > 20 && login.accessToken.length < 16384, 'STUDENT_TOKEN_INVALID');
    token = login.accessToken; password = '';
    const profile = await request('PROFILE_PERSISTED', 'GET', '/api/me/graduation/profile', token);
    ensure(profile.configured === true && profile.profilePublicId === http.profilePublicId
      && profile.organization?.publicId === fixture.organizationPublicId && profile.totalCredits === 3,
      'PROFILE_PERSISTENCE_MISMATCH');
    artifact.profile = { publicId: profile.profilePublicId, credits: profile.totalCredits };
    const evidence = await request('EVIDENCE_PERSISTED', 'GET', `/api/me/evidence-submissions/${http.evidencePublicId}`, token);
    ensure(evidence.publicId === http.evidencePublicId && evidence.casePublicId === http.casePublicId
      && evidence.status === 'VERIFIED' && evidence.caseStatus === 'VERIFIED' && evidence.reviewCount === 2
      && evidence.achievedAssuranceLevel === 'L2', 'EVIDENCE_PERSISTENCE_MISMATCH');
    if (http.syntheticPdfSha256) ensure(evidence.files?.length === 1 && evidence.files[0].sha256 === http.syntheticPdfSha256, 'EVIDENCE_FILE_PERSISTENCE_MISMATCH');
    artifact.evidence = { publicId: evidence.publicId, status: evidence.status, reviews: 2, assurance: 'L2' };
    const courses = await request('COURSE_PERSISTED', 'GET', '/api/me/graduation/courses', token);
    ensure(Array.isArray(courses) && courses.length === 1 && courses[0].publicId === http.coursePublicId
      && courses[0].category === 'MAJOR_REQUIRED', 'COURSE_PERSISTENCE_MISMATCH');
    publicId(courses[0].academicUnitPublicId);
    // The original HTTP artifact stores course ID, not unit ID. When no historical unit snapshot
    // exists, prove only that the persisted mapping belongs to this student institution today.
    const storedUnit = http.courseAcademicUnitPublicId ?? http.academicUnitPublicId;
    if (storedUnit !== undefined) {
      ensure(courses[0].academicUnitPublicId === publicId(storedUnit), 'COURSE_UNIT_PERSISTENCE_MISMATCH');
      artifact.mappingReference = 'HTTP_ARTIFACT';
    } else {
      const units = await request('ACADEMIC_UNIT_REFERENCE', 'GET', '/api/me/graduation/academic-units', token);
      ensure(Array.isArray(units) && units.length > 0, 'ACADEMIC_UNIT_REFERENCE_MISSING');
      ensure(units.some(unit => unit.publicId === courses[0].academicUnitPublicId), 'COURSE_UNIT_OUTSIDE_CURRENT_ORGANIZATION');
      artifact.mappingReference = 'CURRENT_ORGANIZATION_UNIT_LIST_NOT_ORIGINAL_ID_SNAPSHOT';
    }
    artifact.course = { publicId: courses[0].publicId, category: 'MAJOR_REQUIRED', academicUnitPublicId: courses[0].academicUnitPublicId,
      count: 1, mappedPersisted: true };
    const observedOriginals = new Set(); artifact.credentials = [];
    for (const type of TYPES) {
      const credentialId = result.credentials[type];
      const value = await request(`CREDENTIAL_${type}_PERSISTED`, 'GET', `/api/public/credentials/${credentialId}`);
      const chain = value.evidence?.blockchain; const expectedStatus = type === 'PARTICIPATION' ? 'REVOKED' : 'VALID';
      ensure(value.credentialPublicId === credentialId && value.credentialType === type && value.verificationStatus === expectedStatus
        && value.issuerPublicId === fixture.organizationPublicId && chain?.provider === 'SUI' && chain.network === 'testnet'
        && chain.approvalScheme === 'TREKKEY_SUI_APPROVAL_V1' && anchors.has(chain.transactionDigest)
        && String(chain.checkpointSequenceNumber) === anchors.get(chain.transactionDigest), 'CREDENTIAL_PERSISTENCE_MISMATCH');
      if (type !== 'AWARD') observedOriginals.add(chain.transactionDigest);
      artifact.credentials.push({ publicId: credentialId, type, status: expectedStatus, provider: 'SUI' });
    }
    ensure(observedOriginals.size === 2 && [...ORIGINAL_DIGESTS].every(digest => observedOriginals.has(digest)), 'ORIGINAL_PUBLIC_ANCHORS_CHANGED');
    artifact.originalAnchorsPreserved = 2; artifact.confirmedTransactionReferences = 4;
  } finally { token = undefined; password = ''; }
}

export async function directory(path, owners, mode) {
  const stat = await lstat(path);
  ensure(stat.isDirectory() && !stat.isSymbolicLink() && owners.includes(stat.uid)
    && (mode === undefined ? (stat.mode & 0o022) === 0 : (stat.mode & 0o777) === mode)
    && await realpath(path) === path, 'READBACK_DIRECTORY_UNTRUSTED');
}
export async function privateText(path, owners, limit) {
  const file = await open(path, constants.O_RDONLY | constants.O_NOFOLLOW);
  try {
    const stat = await file.stat();
    ensure(stat.isFile() && stat.nlink === 1 && owners.includes(stat.uid) && (stat.mode & 0o777) === 0o600
      && stat.size > 0 && stat.size <= limit, 'READBACK_FILE_UNTRUSTED');
    return await file.readFile('utf8');
  } finally { await file.close(); }
}
export async function run() {
  ensure(process.getuid?.() === 0, 'ROOT_OPERATOR_REQUIRED'); process.umask(0o077);
  const root = DEFAULT_ROOT; const output = join(root, 'full-e2e');
  await directory(root, [0]); await directory(join(root, 'secrets'), [0], 0o700);
  await directory(join(root, 'secrets/full'), [0], 0o700); await directory(output, [0, 10001], 0o700);
  const json = async name => JSON.parse(await privateText(join(output, name), [0, 10001], 100000));
  const fixture = await json('01-fixture.json'), http = await json('http-artifact.json'), result = await json('result.json');
  validateReferences(fixture, http, result);
  let password = await privateText(join(root, 'secrets/full/E2E_LOGIN_PASSWORD'), [10001], 64);
  ensure(/^[0-9a-f]{64}$/.test(password), 'SYNTHETIC_LOGIN_PASSWORD_INVALID');
  const artifact = { status: 'RUNNING', origin: ORIGIN, database: 'trekkey_sui_full', checks: [],
    loginSessionWriteOnly: true, evaluationOrUploadPerformed: false, directChainCalls: false, secretsRecorded: false,
    startedAt: new Date().toISOString() };
  await writeExclusive(join(output, 'restart-readback.claim'), JSON.stringify({ origin: ORIGIN, startedAt: artifact.startedAt }), 10001);
  try {
    await verifyPersistence(fixture, http, result, password, artifact, makeReadbackClient(artifact.checks));
    artifact.status = 'PASS';
  } catch (error) {
    artifact.status = 'FAIL';
    artifact.failureCode = /^[A-Z][A-Z0-9_]{0,120}$/.test(error?.message ?? '') ? error.message : 'PERSISTENCE_READBACK_FAILED';
  } finally { password = ''; artifact.finishedAt = new Date().toISOString(); }
  await writeExclusive(join(output, 'restart-readback.json'), JSON.stringify(artifact, null, 2) + '\n', 10001);
  process.stdout.write(JSON.stringify({ status: artifact.status, failureCode: artifact.failureCode }) + '\n');
  if (artifact.status !== 'PASS') process.exitCode = 1;
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  Promise.resolve().then(() => { parseArguments(process.argv.slice(2)); return run(); }).catch(() => {
    process.stdout.write('{"status":"FAIL","failureCode":"READBACK_REFUSED_OR_INCOMPLETE"}\n'); process.exitCode = 1;
  });
}
