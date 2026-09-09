#!/usr/bin/env node
// Explicit one-shot synthetic HTTP verification. Never reads production settings or calls chain APIs.
import { createHash } from 'node:crypto';
import { constants } from 'node:fs';
import { lstat, open, realpath } from 'node:fs/promises';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { DEFAULT_ROOT, validateRoot, writeExclusive } from './prepare-runtime.mjs';

export const ORIGIN = 'http://127.0.0.1:18080';
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const ensure = (condition, code = 'HTTP_VERIFICATION_REFUSED') => { if (!condition) throw new Error(code); };
const id = value => { ensure(typeof value === 'string' && UUID.test(value), 'INVALID_PUBLIC_ID'); return value; };
export function parseArguments(args) {
  ensure(args[0] === '--run' && args[1] === '--confirmed-isolated-full-database');
  ensure(args.length === 2 || (args.length === 4 && args[2] === '--root'));
  return { root: validateRoot(args[3] ?? DEFAULT_ROOT) };
}
export function validateFixture(input) {
  ensure(input && typeof input === 'object' && !Array.isArray(input));
  id(input.organizationPublicId);
  for (const role of ['admin', 'student', 'secondAdmin']) {
    ensure(typeof input[`${role}Email`] === 'string'
      && /^sui-full-(?:admin|student|second-admin)-[0-9a-f-]{36}@example\.invalid$/.test(input[`${role}Email`]));
    ensure(Number.isSafeInteger(input[`${role}UserId`]) && input[`${role}UserId`] > 0);
  }
  ensure(new Set(['admin', 'student', 'secondAdmin'].map(role => input[`${role}UserId`])).size === 3);
  ensure(new Set(['admin', 'student', 'secondAdmin'].map(role => input[`${role}Email`])).size === 3);
  return input;
}
export function allowedPath(path) {
  return typeof path === 'string' && /^\/api\/(?:auth\/signin|me\/graduation\/(?:profile|academic-units|evaluations|courses(?:\/[0-9a-f-]{36})?|transcript-imports\?apply=true)|me\/evidence-submissions(?:\/[0-9a-f-]{36})?|admin\/evidence-verifications(?:\/[0-9a-f-]{36}(?:\/reviews)?)?)$/.test(path);
}
export function makeClient(checks, transport = fetch) {
  return async function request(step, method, path, token, body, expected = 200) {
    ensure(/^[A-Z0-9_]+$/.test(step) && allowedPath(path), 'REQUEST_SCOPE_REFUSED');
    const headers = { Accept: 'application/json' };
    if (token) headers.Authorization = `Bearer ${token}`;
    if (body !== undefined && !(body instanceof FormData)) {
      headers['Content-Type'] = 'application/json'; body = JSON.stringify(body);
    }
    let response;
    try {
      response = await transport(ORIGIN + path, { method, headers, body, redirect: 'error', signal: AbortSignal.timeout(20000) });
    } catch { throw new Error(`${step}_TRANSPORT_FAILED`); }
    const accepted = (Array.isArray(expected) ? expected : [expected]).includes(response.status);
    const record = { step, httpStatus: response.status, passed: accepted };
    checks.push(record);
    // Never include response bodies/headers in errors, logs or public artifacts.
    if (!accepted) { await response.body?.cancel(); throw new Error(`${step}_HTTP_${response.status}`); }
    const reader = response.body?.getReader();
    ensure(reader, `${step}_RESPONSE_MISSING`);
    const chunks = []; let bytes = 0;
    for (;;) {
      const next = await reader.read(); if (next.done) break;
      bytes += next.value.length;
      if (bytes > 1048576) { await reader.cancel(); throw new Error(`${step}_RESPONSE_TOO_LARGE`); }
      chunks.push(next.value);
    }
    let result;
    try { result = JSON.parse(Buffer.concat(chunks).toString('utf8')); }
    catch { throw new Error(`${step}_INVALID_JSON`); }
    if (response.status >= 400) return result;
    ensure(result && Object.hasOwn(result, 'data'), `${step}_MISSING_DATA`);
    return result.data;
  };
}
export function syntheticPdf() {
  const content = 'BT /F1 12 Tf 30 100 Td (SYNTHETIC TEST ONLY - NOT AN ACADEMIC CREDENTIAL) Tj ET\n';
  const objects = ['<< /Type /Catalog /Pages 2 0 R >>', '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 600 160] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
    `<< /Length ${Buffer.byteLength(content)} >>\nstream\n${content}endstream`];
  let pdf = '%PDF-1.4\n'; const offsets = [0];
  for (let index = 0; index < objects.length; index++) {
    offsets.push(Buffer.byteLength(pdf)); pdf += `${index + 1} 0 obj\n${objects[index]}\nendobj\n`;
  }
  const start = Buffer.byteLength(pdf);
  pdf += `xref\n0 6\n0000000000 65535 f \n${offsets.slice(1).map(offset => `${String(offset).padStart(10, '0')} 00000 n \n`).join('')}`;
  return Buffer.from(pdf + `trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n${start}\n%%EOF\n`, 'ascii');
}
const profileRequest = {
  admissionYear: 2021, curriculumYear: 2021, admissionType: 'FRESHMAN', graduationPath: 'REGULAR',
  majorPlanType: 'CONVERGENCE_I', registeredSemesters: 8, totalCredits: 130, hansungCredits: 130,
  transferRecognizedCredits: 0, cumulativeGpa: 3.5, gpaScale: 4.5, activityPoints: 800,
  internationalStudent: false, teachingProgram: false, inputMode: 'SUMMARY_ONLY', recordCompleteness: 'UNKNOWN',
  academicUnits: [], failHistoryStatus: 'NONE', recordCompletenessConfirmed: false, version: null,
};
export async function workflow(fixture, password, artifact, request) {
  validateFixture(fixture);
  const tokens = {};
  try {
    await request('UNAUTHENTICATED_PROFILE', 'GET', '/api/me/graduation/profile', null, undefined, [401, 403]);
    for (const role of ['admin', 'student', 'secondAdmin']) {
      const data = await request(`SIGNIN_${role.toUpperCase()}`, 'POST', '/api/auth/signin', null,
        { email: fixture[`${role}Email`], password });
      ensure(data.userSessionRes?.id === fixture[`${role}UserId`]
        && data.userSessionRes.email === fixture[`${role}Email`]
        && data.userSessionRes.role === (role === 'student' ? 'PARTICIPANT' : 'ADMIN'), 'SIGNIN_IDENTITY_MISMATCH');
      ensure(typeof data.accessToken === 'string' && data.accessToken.length > 20 && data.accessToken.length < 16384, 'SIGNIN_TOKEN_INVALID');
      tokens[role] = data.accessToken;
    }
    password = '';
    await request('STUDENT_ADMIN_QUEUE_DENIED', 'GET', '/api/admin/evidence-verifications', tokens.student, undefined, 403);
    await request('ADMIN_PARTICIPANT_PROFILE_DENIED', 'GET', '/api/me/graduation/profile', tokens.admin, undefined, 403);
    const emptyProfile = await request('PROFILE_INITIAL', 'GET', '/api/me/graduation/profile', tokens.student);
    ensure(emptyProfile.configured === false && emptyProfile.organization?.publicId === fixture.organizationPublicId, 'FRESH_SYNTHETIC_PROFILE_REQUIRED');
    const emptyEvidence = await request('EVIDENCE_INITIAL', 'GET', '/api/me/evidence-submissions', tokens.student);
    ensure(Array.isArray(emptyEvidence) && emptyEvidence.length === 0, 'FRESH_SYNTHETIC_EVIDENCE_REQUIRED');
    const profile = await request('PROFILE_CREATE', 'PUT', '/api/me/graduation/profile', tokens.student, profileRequest);
    ensure(profile.configured === true && profile.organization?.publicId === fixture.organizationPublicId, 'PROFILE_IDENTITY_MISMATCH');
    artifact.profilePublicId = id(profile.profilePublicId);
    const readback = await request('PROFILE_READBACK', 'GET', '/api/me/graduation/profile', tokens.student);
    ensure(readback.profilePublicId === profile.profilePublicId && readback.totalCredits === 130, 'PROFILE_READBACK_MISMATCH');
    const evaluate = async step => {
      const value = await request(step, 'POST', '/api/me/graduation/evaluations', tokens.student, {}, 201);
      ensure(['ELIGIBLE', 'NOT_ELIGIBLE', 'INDETERMINATE'].includes(value.status)
        && Array.isArray(value.policies) && value.policies.length > 0, 'EVALUATION_CONTRACT_INVALID');
      artifact.evaluations.push({ step, evaluationPublicId: id(value.evaluationPublicId), observedStatus: value.status,
        policyCount: value.policies.length, requirementCount: value.requirements?.length ?? 0 });
    };
    // This real request invokes TrekkeyGraduationEvidenceSyncService's native MySQL history query.
    // HTTP success proves execution, not the contents/count of imported native records.
    await evaluate('EVALUATE_BEFORE_EXTERNAL_EVIDENCE');
    const pdf = syntheticPdf(); const hash = createHash('sha256').update(pdf).digest('hex');
    const form = new FormData();
    form.append('request', new Blob([JSON.stringify({ evidenceType: 'GRADUATION_WORK', targetRecordType: 'GRADUATION_WORK',
      title: 'SYNTHETIC HTTP TEST - NOT A REAL GRADUATION WORK', issuerName: 'SYNTHETIC TEST ONLY' })], { type: 'application/json' }));
    form.append('files', new Blob([pdf], { type: 'application/pdf' }), 'synthetic-http-evidence.pdf');
    const submitted = await request('EVIDENCE_MULTIPART_CREATE', 'POST', '/api/me/evidence-submissions', tokens.student, form, 201);
    artifact.evidencePublicId = id(submitted.publicId); artifact.casePublicId = id(submitted.casePublicId);
    ensure(submitted.files?.length === 1 && submitted.files[0].sha256 === hash, 'EVIDENCE_FILE_HASH_MISMATCH');
    artifact.syntheticPdfSha256 = hash;
    const reviewPath = `/api/admin/evidence-verifications/${artifact.casePublicId}/reviews`;
    // Stored synthetic reference only. Neither this script nor the review service fetches this URL.
    const review = { result: 'APPROVE', assuranceLevel: 'L2', reasonCode: 'OFFICIAL_SOURCE_MATCH',
      officialReferenceUrl: 'https://example.invalid/synthetic-test-only', note: 'Synthetic test approval; NOT an official academic decision.' };
    await request('STUDENT_REVIEW_DENIED', 'POST', reviewPath, tokens.student, review, 403);
    const first = await request('ADMIN_FIRST_REVIEW', 'POST', reviewPath, tokens.admin, review);
    ensure(first.caseStatus === 'AWAITING_SECOND_REVIEW' && first.reviewCount === 1 && first.finalDecision === null, 'FIRST_REVIEW_INVALID');
    const duplicate = await request('DUPLICATE_ADMIN_REVIEW_DENIED', 'POST', reviewPath, tokens.admin, review, 409);
    ensure(duplicate.code === 'EVIDENCE_REVIEWER_CONFLICT', 'DUPLICATE_REVIEW_ERROR_CODE_INVALID');
    const afterDuplicate = await request('DUPLICATE_REVIEW_READBACK', 'GET', `/api/admin/evidence-verifications/${artifact.casePublicId}`, tokens.admin);
    ensure(afterDuplicate.reviewCount === 1 && afterDuplicate.caseStatus === 'AWAITING_SECOND_REVIEW', 'DUPLICATE_REVIEW_CHANGED_STATE');
    await evaluate('EVALUATE_AFTER_ONE_REVIEW');
    const second = await request('SECOND_ADMIN_REVIEW', 'POST', reviewPath, tokens.secondAdmin, review);
    ensure(second.caseStatus === 'VERIFIED' && second.finalDecision === 'VERIFIED' && second.reviewCount === 2, 'SECOND_REVIEW_INVALID');
    const verified = await request('VERIFIED_EVIDENCE_READBACK', 'GET', `/api/me/evidence-submissions/${artifact.evidencePublicId}`, tokens.student);
    ensure(verified.status === 'VERIFIED' && verified.caseStatus === 'VERIFIED' && verified.reviewCount === 2
      && verified.achievedAssuranceLevel === 'L2', 'VERIFIED_EVIDENCE_READBACK_INVALID');
    artifact.evidenceDecision = 'VERIFIED';
    await evaluate('EVALUATE_AFTER_TWO_REVIEWS');

    // Run course mapping last: failures remain visible without hiding the earlier evidence observations.
    const courses = await request('COURSES_INITIAL', 'GET', '/api/me/graduation/courses', tokens.student);
    ensure(Array.isArray(courses) && courses.length === 0, 'FRESH_SYNTHETIC_COURSES_REQUIRED');
    const units = await request('ACADEMIC_UNITS_LIST', 'GET', '/api/me/graduation/academic-units', tokens.student);
    ensure(Array.isArray(units) && units.length > 0, 'SEEDED_ACADEMIC_UNITS_REQUIRED');
    const unitId = id((units.find(unit => ['TRACK', 'MAJOR', 'DEPARTMENT'].includes(unit.unitType)) ?? units[0]).publicId);
    const csv = 'term,courseCode,courseName,credits,grade,category\n2021-1,SYNHTTP001,Synthetic HTTP Course,3,A+,MAJOR_ELECTIVE\n';
    const transcript = new FormData(); transcript.append('file', new Blob([csv], { type: 'text/csv' }), 'synthetic-http-transcript.csv');
    const imported = await request('COURSE_CSV_IMPORT', 'POST', '/api/me/graduation/transcript-imports?apply=true', tokens.student, transcript);
    ensure(imported.applied === true && imported.courseCount === 1, 'COURSE_IMPORT_INVALID');
    const importedCourses = await request('COURSES_AFTER_IMPORT', 'GET', '/api/me/graduation/courses', tokens.student);
    ensure(Array.isArray(importedCourses) && importedCourses.length === 1 && importedCourses[0].courseCode === 'SYNHTTP001', 'COURSE_READBACK_INVALID');
    artifact.coursePublicId = id(importedCourses[0].publicId);
    const mapped = await request('COURSE_PATCH_WITH_UNIT', 'PATCH', `/api/me/graduation/courses/${artifact.coursePublicId}`,
      tokens.student, { category: 'MAJOR_REQUIRED', academicUnitPublicId: unitId });
    ensure(mapped.category === 'MAJOR_REQUIRED' && mapped.academicUnitPublicId === unitId, 'COURSE_MAPPING_INVALID');
    const mappedCourses = await request('COURSE_MAPPING_READBACK', 'GET', '/api/me/graduation/courses', tokens.student);
    ensure(mappedCourses.length === 1 && mappedCourses[0].category === 'MAJOR_REQUIRED'
      && mappedCourses[0].academicUnitPublicId === unitId, 'COURSE_MAPPING_READBACK_INVALID');
    await evaluate('EVALUATE_AFTER_COURSE_IMPORT');
  } finally { password = ''; for (const role of Object.keys(tokens)) tokens[role] = ''; }
}
async function directory(path, allowedOwners, exactMode) {
  const stat = await lstat(path);
  ensure(stat.isDirectory() && !stat.isSymbolicLink() && allowedOwners.includes(stat.uid)
    && (exactMode === undefined ? (stat.mode & 0o022) === 0 : (stat.mode & 0o777) === exactMode)
    && await realpath(path) === path, 'INPUT_DIRECTORY_UNTRUSTED');
}
async function readInput(path, maxSize, owners) {
  const file = await open(path, constants.O_RDONLY | constants.O_NOFOLLOW);
  try {
    const stat = await file.stat();
    ensure(stat.isFile() && stat.nlink === 1 && owners.includes(stat.uid) && (stat.mode & 0o777) === 0o600
      && stat.size > 0 && stat.size <= maxSize, 'INPUT_FILE_UNTRUSTED');
    return await file.readFile('utf8');
  } finally { await file.close(); }
}
export async function run(root) {
  validateRoot(root); ensure(process.getuid?.() === 0, 'ROOT_OPERATOR_REQUIRED'); process.umask(0o077);
  await directory(root, [0]); await directory(join(root, 'secrets'), [0], 0o700);
  await directory(join(root, 'secrets/full'), [0], 0o700);
  const output = join(root, 'full-e2e'); await directory(output, [0, 10001], 0o700);
  const fixture = validateFixture(JSON.parse(await readInput(join(output, '01-fixture.json'), 32768, [0, 10001])));
  const chainClaim = JSON.parse(await readInput(join(output, 'run.claim'), 4096, [0, 10001]));
  ensure(chainClaim.database === 'trekkey_sui_full' && chainClaim.network === 'testnet', 'ISOLATED_FIXTURE_CLAIM_REQUIRED');
  let password = await readInput(join(root, 'secrets/full/E2E_LOGIN_PASSWORD'), 64, [10001]);
  ensure(/^[0-9a-f]{64}$/.test(password), 'SYNTHETIC_LOGIN_PASSWORD_FORMAT_INVALID');
  const artifact = { status: 'RUNNING', origin: ORIGIN, database: 'trekkey_sui_full', synthetic: true,
    startedAt: new Date().toISOString(), checks: [], evaluations: [], officialEligibilityAsserted: false,
    policySyncOrPublishPerformed: false, nativeCredentialImportRowCountVerified: false, secretsRecorded: false };
  // fsync before the first login (which writes a refresh session). No automatic retry/reset/cleanup.
  await writeExclusive(join(output, 'http-run.claim'), JSON.stringify({ origin: ORIGIN, database: artifact.database,
    synthetic: true, startedAt: artifact.startedAt }), 10001);
  try {
    await workflow(fixture, password, artifact, makeClient(artifact.checks));
    artifact.status = 'PASS';
  } catch (error) {
    artifact.status = 'FAIL';
    artifact.failureCode = typeof error?.message === 'string' && /^[A-Z][A-Z0-9_]{0,120}$/.test(error.message)
      ? error.message : 'HTTP_VERIFICATION_FAILED';
  } finally { password = ''; artifact.finishedAt = new Date().toISOString(); }
  await writeExclusive(join(output, 'http-artifact.json'), JSON.stringify(artifact, null, 2) + '\n', 10001);
  process.stdout.write(JSON.stringify({ status: artifact.status, checks: artifact.checks.length,
    failureCode: artifact.failureCode, artifact: 'full-e2e/http-artifact.json', secretsRecorded: false }) + '\n');
  if (artifact.status !== 'PASS') process.exitCode = 1;
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  Promise.resolve().then(() => run(parseArguments(process.argv.slice(2)).root)).catch(() => {
    process.stderr.write('HTTP verification refused or incomplete. Preserve claim/artifacts; do not rerun against populated state.\n');
    process.exitCode = 1;
  });
}
