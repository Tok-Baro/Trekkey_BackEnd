#!/usr/bin/env node
// One synthetic sign-in and one evaluation snapshot; all business mutation probes must return 403.
import { lstat } from 'node:fs/promises';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { DEFAULT_ROOT, writeExclusive } from './prepare-runtime.mjs';
import { ORIGIN } from './verify-full-http.mjs';
import { allowedRequest as allowedReadbackRequest, directory, privateText, makeReadbackClient,
  validateReferences, verifyPersistence } from './verify-full-persistence.mjs';

export const CLAIM_NAME = 'completion-v7.claim';
export const RESULT_NAME = 'completion-v7-result.json';
export const CLAIM_FIELDS = ['canonicalPayloadMatches', 'contentHashMatches', 'fileManifestHashMatches',
  'credentialClaimsMatch', 'credentialIdMatches', 'merkleProofMatches'];
export const MANUAL_API = '/api/admin/contests/{contestPublicId}/teams/{teamPublicId}/submission';
export const JUDGE_API = '/api/admin/contests/{publicId}/judges/{judgeId}';
const JWT_SCHEME = 'JWT Authentication';
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const METHODS = new Set(['get', 'put', 'post', 'delete', 'options', 'head', 'patch', 'trace']);
const ensure = (condition, code) => { if (!condition) throw new Error(code); };
const object = value => value !== null && typeof value === 'object' && !Array.isArray(value);

export function parseArguments(args) {
  ensure(args.length === 1 && args[0] === '--verify-completion-v7', 'EXPLICIT_COMPLETION_REQUIRED');
  return DEFAULT_ROOT;
}

export function denialPaths(fixture) {
  ensure(UUID.test(fixture?.contestPublicId ?? '') && UUID.test(fixture?.teamPublicId ?? ''), 'DENIAL_FIXTURE_SCOPE_INVALID');
  return {
    submission: `/api/admin/contests/${fixture.contestPublicId}/teams/${fixture.teamPublicId}/submission`,
    // The workflow has no judge fixture. Zero intentionally names no judge; an empty
    // request also cannot pass DTO validation if an authorization regression occurs.
    judge: `/api/admin/contests/${fixture.contestPublicId}/judges/0`
  };
}

function completionReferences(fixture, http, result) {
  validateReferences(fixture, http, result);
  const denied = denialPaths(fixture);
  ensure(result.fixture.contestPublicId === fixture.contestPublicId && result.fixture.teamPublicId === fixture.teamPublicId,
    'COMPLETION_FIXTURE_IDENTITY_MISMATCH');
  return denied;
}

export function allowedRequest(method, path, fixture) {
  if (allowedReadbackRequest(method, path)) return true;
  const denied = denialPaths(fixture);
  return (method === 'GET' && path === '/v3/api-docs')
    || (method === 'POST' && path === '/api/me/graduation/evaluations')
    || (method === 'POST' && path === denied.submission)
    || (method === 'PATCH' && path === denied.judge);
}

async function boundedJson(response, step, limit) {
  const reader = response.body?.getReader(); ensure(reader, `${step}_EMPTY_RESPONSE`);
  const chunks = []; let size = 0;
  for (;;) {
    let next;
    try { next = await reader.read(); } catch { throw new Error(`${step}_READ_FAILED`); }
    if (next.done) break;
    size += next.value.length;
    if (size > limit) { await reader.cancel().catch(() => undefined); throw new Error(`${step}_RESPONSE_TOO_LARGE`); }
    chunks.push(next.value);
  }
  try { return JSON.parse(Buffer.concat(chunks).toString('utf8')); }
  catch { throw new Error(`${step}_INVALID_JSON`); }
}

export function makeCompletionClient(checks, fixture, transport = fetch) {
  const readback = makeReadbackClient(checks, transport);
  const denied = denialPaths(fixture);
  const attemptedWrites = new Set();
  return async (step, method, path, token, body) => {
    ensure(/^[A-Z0-9_]+$/.test(step) && allowedRequest(method, path, fixture), 'COMPLETION_SCOPE_REFUSED');
    if (method !== 'GET') {
      ensure(!attemptedWrites.has(path), 'COMPLETION_WRITE_ALREADY_ATTEMPTED');
      if (path === '/api/auth/signin') {
        ensure(!token && object(body) && Object.keys(body).length === 2 && body.email === fixture.studentEmail
          && /^[0-9a-f]{64}$/.test(body.password ?? ''), 'COMPLETION_STUDENT_LOGIN_ONLY');
      } else {
        ensure(typeof token === 'string' && token.length > 20 && token.length < 16384, 'COMPLETION_TOKEN_REQUIRED');
        if (path === '/api/me/graduation/evaluations') {
          ensure(object(body) && Object.keys(body).length === 0, 'COMPLETION_EMPTY_EVALUATION_ONLY');
        } else ensure(body === undefined, 'COMPLETION_ADMIN_PROBE_BODY_REFUSED');
      }
      attemptedWrites.add(path); // Reserve before transport; even uncertain requests are never retried.
    }
    if (allowedReadbackRequest(method, path)) return readback(step, method, path, token, body);
    ensure(method !== 'GET' || (body === undefined && !token), 'COMPLETION_PUBLIC_DOCS_ONLY');
    const isDenial = path === denied.submission || path === denied.judge;
    const expected = isDenial ? 403 : method === 'POST' ? 201 : 200;
    const headers = { Accept: 'application/json' };
    if (token) headers.Authorization = `Bearer ${token}`;
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    let response;
    try {
      response = await transport(ORIGIN + path, { method, headers,
        body: body === undefined ? undefined : JSON.stringify(body), redirect: 'error', signal: AbortSignal.timeout(20000) });
    } catch { throw new Error(`${step}_TRANSPORT_FAILED`); }
    checks.push({ step, httpStatus: response.status, passed: response.status === expected });
    if (response.status !== expected) {
      await response.body?.cancel().catch(() => undefined);
      throw new Error(`${step}_HTTP_${response.status}`);
    }
    if (isDenial) { await response.body?.cancel().catch(() => undefined); return null; }
    const value = await boundedJson(response, step, path === '/v3/api-docs' ? 2097152 : 1048576);
    if (path === '/v3/api-docs') return value;
    ensure(value?.isSuccess === true && Object.hasOwn(value, 'data'), `${step}_INVALID_ENVELOPE`);
    return value.data;
  };
}

export function validatePublicClaims(value) {
  ensure(UUID.test(value?.credentialPublicId ?? ''), 'PUBLIC_CREDENTIAL_ID_INVALID');
  for (const field of CLAIM_FIELDS) ensure(value.evidence?.[field] === true, 'PUBLIC_SIX_CLAIMS_NOT_TRUE');
  ensure(Array.isArray(value.publicSubjects) && value.publicSubjects.length > 0, 'PUBLIC_SUBJECTS_REQUIRED');
  value.publicSubjects.forEach((subject, index) => ensure(
    subject?.subjectRef === `public-subject:${value.credentialPublicId}:${index}`, 'PUBLIC_SUBJECT_ALIAS_INVALID'));
  return { publicId: value.credentialPublicId, allSixClaims: true, publicSubjectCount: value.publicSubjects.length,
    subjectRefsScopedToCredential: true };
}

export function validateSwagger(api) {
  ensure(object(api) && /^3\./.test(api.openapi ?? '') && object(api.paths), 'SWAGGER_DOCUMENT_INVALID');
  const operations = [];
  for (const [path, item] of Object.entries(api.paths)) {
    ensure(path.startsWith('/') && object(item), 'SWAGGER_PATH_INVALID');
    for (const [method, operation] of Object.entries(item)) {
      if (!METHODS.has(method)) continue;
      ensure(object(operation) && object(operation.responses), 'SWAGGER_OPERATION_INVALID');
      operations.push({ path, method, operation });
    }
  }
  ensure(operations.length === 108, 'SWAGGER_OPERATION_COUNT_MISMATCH');
  ensure(object(api.paths[MANUAL_API]?.post) && object(api.paths[JUDGE_API]?.patch), 'SWAGGER_NEW_ADMIN_APIS_MISSING');
  const scheme = api.components?.securitySchemes?.[JWT_SCHEME];
  ensure(scheme?.type === 'http' && scheme.scheme === 'bearer' && scheme.bearerFormat === 'JWT', 'SWAGGER_JWT_SCHEME_INVALID');
  const publicOperations = operations.filter(({ path }) => path.startsWith('/api/public/') || path === '/api/auth/signin');
  ensure(publicOperations.some(({ path, method }) => path === '/api/public/credentials/{credentialPublicId}' && method === 'get'),
    'SWAGGER_PUBLIC_CREDENTIAL_MISSING');
  for (const { operation } of publicOperations) {
    ensure(Array.isArray(operation.security) && operation.security.length === 0, 'SWAGGER_PUBLIC_REQUIRES_AUTH');
  }
  const adminOperations = operations.filter(({ path }) => path.startsWith('/api/admin/') || path.startsWith('/api/root/'));
  ensure(adminOperations.length > 30, 'SWAGGER_ADMIN_COVERAGE_MISSING');
  for (const { operation } of adminOperations) {
    const security = operation.security;
    ensure(Array.isArray(security) && security.length === 1 && object(security[0])
      && Object.keys(security[0]).length === 1 && Array.isArray(security[0][JWT_SCHEME])
      && security[0][JWT_SCHEME].length === 0, 'SWAGGER_ADMIN_JWT_MISSING');
  }
  return { operationCount: 108, newAdminApis: 2, publicOperationsWithoutJwt: publicOperations.length,
    adminOperationsWithJwt: adminOperations.length };
}

export function validateEvaluation(value) {
  ensure(UUID.test(value?.evaluationPublicId ?? '') && ['NOT_ELIGIBLE', 'INDETERMINATE'].includes(value.status),
    'EVALUATION_STATUS_UNSAFE_OR_INVALID');
  ensure(value.coverage?.complete === false && Array.isArray(value.coverage.gaps) && value.coverage.gaps.length >= 1
    && value.coverage.gaps.every(gap => typeof gap?.code === 'string' && /^[A-Z][A-Z0-9_]{0,99}$/.test(gap.code)),
    'EVALUATION_PARTIAL_COVERAGE_MISSING');
  return { publicId: value.evaluationPublicId, observedStatus: value.status, coverageComplete: false,
    coverageGapCount: value.coverage.gaps.length, overallEligibilityNotAsserted: true };
}

export async function verifyCompletion(fixture, http, result, password, artifact, request) {
  const denied = completionReferences(fixture, http, result);
  let studentToken;
  artifact.publicClaims = [];
  try {
    await verifyPersistence(fixture, http, result, password, artifact, async (...args) => {
      const value = await request(...args);
      if (args[0] === 'STUDENT_SIGNIN') studentToken = value?.accessToken;
      if (/^CREDENTIAL_(?:PARTICIPATION|WORK|AWARD)_PERSISTED$/.test(args[0])) {
        artifact.publicClaims.push(validatePublicClaims(value));
      }
      return value;
    });
    password = '';
    ensure(artifact.checks.length === 8 && artifact.publicClaims.length === 3, 'EIGHT_PERSISTENCE_CHECKS_REQUIRED');
    artifact.persistenceChecks = 8;
    artifact.swagger = validateSwagger(await request('SWAGGER_COMPLETION', 'GET', '/v3/api-docs'));
    await request('STUDENT_MANUAL_SUBMISSION_DENIED', 'POST', denied.submission, studentToken);
    await request('STUDENT_JUDGE_UPDATE_DENIED', 'PATCH', denied.judge, studentToken);
    artifact.adminDenialProbes = { emptyRequests: 2, forbidden: 2, businessMutationsPerformed: false };
    artifact.evaluation = validateEvaluation(await request('PARTIAL_COVERAGE_EVALUATION', 'POST',
      '/api/me/graduation/evaluations', studentToken, {}));
    ensure(artifact.checks.length === 12 && artifact.checks.every(check => check.passed === true), 'COMPLETION_CHECKS_INCOMPLETE');
  } finally { studentToken = undefined; password = ''; }
}

export async function claimCompletion(output, startedAt, uid = 10001) {
  try { await lstat(join(output, RESULT_NAME)); }
  catch (error) { if (error?.code !== 'ENOENT') throw error; else {
    await writeExclusive(join(output, CLAIM_NAME), JSON.stringify({ origin: ORIGIN, database: 'trekkey_sui_full',
      synthetic: true, startedAt, maximumSignins: 1, maximumEvaluationSnapshots: 1,
      uploadsContestReviewOrChainMutationsAllowed: false }), uid);
    return;
  } }
  throw new Error('COMPLETION_RESULT_ALREADY_EXISTS');
}

export async function run() {
  ensure(process.getuid?.() === 0, 'ROOT_OPERATOR_REQUIRED'); process.umask(0o077);
  const root = DEFAULT_ROOT, output = join(root, 'full-e2e');
  await directory(root, [0]); await directory(join(root, 'secrets'), [0], 0o700);
  await directory(join(root, 'secrets/full'), [0], 0o700); await directory(output, [0, 10001], 0o700);
  const json = async name => JSON.parse(await privateText(join(output, name), [0, 10001], 100000));
  const fixture = await json('01-fixture.json'), http = await json('http-artifact.json'), result = await json('result.json');
  completionReferences(fixture, http, result);
  const chainClaim = await json('run.claim');
  ensure(chainClaim.database === 'trekkey_sui_full' && chainClaim.network === 'testnet', 'ISOLATED_FIXTURE_CLAIM_REQUIRED');
  let password = await privateText(join(root, 'secrets/full/E2E_LOGIN_PASSWORD'), [10001], 64);
  ensure(/^[0-9a-f]{64}$/.test(password), 'SYNTHETIC_LOGIN_PASSWORD_INVALID');
  const artifact = { status: 'RUNNING', origin: ORIGIN, database: 'trekkey_sui_full', synthetic: true,
    checks: [], maximumLoginSessionsWritten: 1, maximumEvaluationSnapshotsWritten: 1,
    uploadsContestOrReviewMutationsPerformed: false, directChainCalls: false, secretsRecorded: false,
    startedAt: new Date().toISOString() };
  await claimCompletion(output, artifact.startedAt);
  try {
    await verifyCompletion(fixture, http, result, password, artifact, makeCompletionClient(artifact.checks, fixture));
    artifact.status = 'PASS';
  } catch (error) {
    artifact.status = 'FAIL'; artifact.failureCode = /^[A-Z][A-Z0-9_]{0,120}$/.test(error?.message ?? '')
      ? error.message : 'COMPLETION_HTTP_FAILED';
  } finally { password = ''; artifact.finishedAt = new Date().toISOString(); }
  await writeExclusive(join(output, RESULT_NAME), JSON.stringify(artifact, null, 2) + '\n', 10001);
  process.stdout.write(JSON.stringify({ status: artifact.status, checks: artifact.checks.length,
    failureCode: artifact.failureCode, artifact: `full-e2e/${RESULT_NAME}`, secretsRecorded: false }) + '\n');
  if (artifact.status !== 'PASS') process.exitCode = 1;
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  Promise.resolve().then(() => { parseArguments(process.argv.slice(2)); return run(); }).catch(() => {
    process.stdout.write('{"status":"FAIL","failureCode":"COMPLETION_REFUSED_OR_INCOMPLETE"}\n'); process.exitCode = 1;
  });
}
