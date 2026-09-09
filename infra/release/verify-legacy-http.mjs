#!/usr/bin/env node
// Public HTTP compatibility gate only; never an original-ledger or production-release attestation.
import { constants } from 'node:fs';
import { open, lstat } from 'node:fs/promises';
import { dirname, isAbsolute } from 'node:path';
import { pathToFileURL } from 'node:url';
import { createHash } from 'node:crypto';

export const ORIGINS = Object.freeze({ capture: 'http://127.0.0.1:8080', verify: 'http://127.0.0.1:18082' });
export const CLAIMS = ['canonicalPayloadMatches', 'contentHashMatches', 'fileManifestHashMatches',
  'credentialClaimsMatch', 'credentialIdMatches', 'merkleProofMatches'];
const HASHES = ['issuerId', 'credentialIdHash', 'schemaVersionHash', 'contentHash', 'fileManifestHash',
  'leafHash', 'batchIdHash', 'merkleRoot'];
const TOP = ['credentialPublicId', 'verificationStatus', 'credentialType', 'schemaProfileId',
  'issuerPublicId', 'issuedAt', 'expiresAt', 'publicSummaryHash', 'evidence'];
const EVIDENCE = [...CLAIMS, ...HASHES, 'batchPublicId', 'treeVersion', 'merkleProof',
  'chainId', 'contractAddress', 'transactionHash', 'blockNumber'];
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const HASH = /^0x[0-9a-f]{64}$/;
const ADDRESS = /^0x[0-9a-f]{40}$/;
const ADDRESS_INPUT = /^0x[0-9a-fA-F]{40}$/;
const STATUSES = new Set(['VALID', 'REVOKED', 'SUPERSEDED', 'EXPIRED']);
const ensure = (ok, code) => { if (!ok) throw new Error(code); };
const object = value => value !== null && typeof value === 'object' && !Array.isArray(value);
const exact = (value, keys) => object(value) && Object.keys(value).length === keys.length
  && keys.every(key => Object.hasOwn(value, key));
const date = value => typeof value === 'string' && value.length <= 40
  && /^\d{4}-\d\d-\d\dT/.test(value) && Number.isFinite(Date.parse(value));

export function validateIds(ids) {
  ensure(Array.isArray(ids) && ids.length === 6 && ids.every(id => typeof id === 'string' && UUID.test(id))
    && new Set(ids).size === 6, 'SIX_DISTINCT_UUIDS_REQUIRED');
  return ids;
}

function validateRecord(record) {
  ensure(exact(record, TOP) && exact(record.evidence, EVIDENCE), 'REFERENCE_FIELDS_INVALID');
  const e = record.evidence;
  ensure(UUID.test(record.credentialPublicId) && UUID.test(record.issuerPublicId)
    && record.credentialType === 'AWARD' && record.schemaProfileId === 'trekkey:award:v1:jcs-rfc8785:unicode-nfc-1'
    && STATUSES.has(record.verificationStatus) && date(record.issuedAt)
    && (record.expiresAt === null || date(record.expiresAt)) && HASH.test(record.publicSummaryHash), 'CREDENTIAL_CONTRACT_INVALID');
  ensure(CLAIMS.every(key => e[key] === true), 'LOCAL_CLAIMS_NOT_ALL_TRUE');
  ensure(HASHES.every(key => typeof e[key] === 'string' && HASH.test(e[key]))
    && UUID.test(e.batchPublicId) && Number.isInteger(e.treeVersion) && e.treeVersion > 0
    && e.treeVersion <= 65535 && Array.isArray(e.merkleProof) && e.merkleProof.length <= 64
    && e.merkleProof.every(hash => typeof hash === 'string' && HASH.test(hash)), 'EVIDENCE_CONTRACT_INVALID');
  ensure(e.chainId === 1001 && typeof e.contractAddress === 'string' && ADDRESS.test(e.contractAddress)
    && e.contractAddress !== `0x${'0'.repeat(40)}` && typeof e.transactionHash === 'string'
    && HASH.test(e.transactionHash) && e.transactionHash !== `0x${'0'.repeat(64)}`
    && Number.isSafeInteger(e.blockNumber) && e.blockNumber > 0, 'LEGACY_COORDINATES_INVALID');
  return record;
}

export function projectResponse(envelope, id, mode) {
  ensure(object(envelope) && object(envelope.data), 'RESPONSE_ENVELOPE_INVALID');
  const data = envelope.data;
  ensure(data.credentialPublicId === id && object(data.evidence), 'CREDENTIAL_IDENTITY_MISMATCH');
  if (mode === 'verify') {
    const chain = data.evidence.blockchain;
    ensure(object(chain) && chain.provider === 'KAIA' && chain.network === 'kairos'
      && chain.approvalScheme === 'EIP712_V1', 'LEGACY_PROVIDER_NOT_CONFIRMED');
    ensure(Array.isArray(data.publicSubjects) && data.publicSubjects.length > 0
      && data.publicSubjects.every((subject, index) => object(subject)
        && subject.subjectRef === `public-subject:${id}:${index}`), 'PUBLIC_SUBJECT_PRIVACY_FAILED');
    ensure(!/user:\d+/.test(JSON.stringify(data)), 'INTERNAL_USER_REFERENCE_EXPOSED');
  } else {
    ensure(mode === 'capture', 'MODE_INVALID');
    if (data.evidence.blockchain != null) ensure(data.evidence.blockchain.provider === 'KAIA', 'LEGACY_PROVIDER_INVALID');
  }
  const record = Object.fromEntries(TOP.map(key => [key, data[key]]));
  ensure(Array.isArray(data.publicSubjects), 'PUBLIC_SUBJECTS_INVALID');
  // Compare the remaining public display contract without retaining any names, numbers or detail text.
  const sorted = value => Array.isArray(value) ? value.map(sorted) : object(value)
    ? Object.fromEntries(Object.keys(value).sort().map(key => [key, sorted(value[key])])) : value;
  const summary = { credentialNo: data.credentialNo, issuerName: data.issuerName, publicDetails: data.publicDetails,
    replacementCredentialPublicId: data.replacementCredentialPublicId,
    replacementCredentialIdHash: data.replacementCredentialIdHash,
    publicSubjects: data.publicSubjects.map(({ subjectRef, ...subject }) => subject) };
  record.publicSummaryHash = `0x${createHash('sha256').update(JSON.stringify(sorted(summary))).digest('hex')}`;
  record.evidence = Object.fromEntries(EVIDENCE.map(key => [key, data.evidence[key]]));
  // EIP-55 casing is a display encoding, not a different 20-byte contract identity.
  // Validate the exact input width/alphabet before normalizing; never trim or pad addresses.
  ensure(typeof record.evidence.contractAddress === 'string' && ADDRESS_INPUT.test(record.evidence.contractAddress),
    'LEGACY_COORDINATES_INVALID');
  record.evidence.contractAddress = record.evidence.contractAddress.toLowerCase();
  return validateRecord(record);
}

export function validateReference(reference) {
  ensure(exact(reference, ['version', 'records']) && reference.version === 1
    && Array.isArray(reference.records), 'REFERENCE_INVALID');
  validateIds(reference.records.map(record => record?.credentialPublicId));
  reference.records.forEach(validateRecord);
  return reference;
}

export async function privateJson(path) {
  ensure(isAbsolute(path), 'ABSOLUTE_PRIVATE_PATH_REQUIRED');
  const handle = await open(path, constants.O_RDONLY | constants.O_NOFOLLOW);
  try {
    const info = await handle.stat();
    ensure(info.isFile() && info.nlink === 1 && (info.mode & 0o777) === 0o600
      && info.uid === process.getuid() && info.size > 0 && info.size <= 131072, 'PRIVATE_FILE_REQUIRED');
    const bytes = Buffer.alloc(131073);
    const { bytesRead } = await handle.read(bytes, 0, bytes.length, 0);
    ensure(bytesRead <= 131072, 'PRIVATE_FILE_TOO_LARGE');
    try { return JSON.parse(bytes.subarray(0, bytesRead).toString('utf8')); }
    catch { throw new Error('PRIVATE_JSON_INVALID'); }
  } finally { await handle.close(); }
}

export async function writeReference(path, reference) {
  validateReference(reference);
  ensure(isAbsolute(path), 'ABSOLUTE_PRIVATE_PATH_REQUIRED');
  const parent = await lstat(dirname(path));
  ensure(parent.isDirectory() && !parent.isSymbolicLink() && (parent.mode & 0o777) === 0o700
    && parent.uid === process.getuid(), 'PRIVATE_DIRECTORY_REQUIRED');
  const handle = await open(path, constants.O_WRONLY | constants.O_CREAT | constants.O_EXCL | constants.O_NOFOLLOW, 0o600);
  try { await handle.writeFile(`${JSON.stringify(reference)}\n`); await handle.sync(); }
  finally { await handle.close(); }
}

export function makeClient(mode, ids, transport = fetch, live = false) {
  ensure(Object.hasOwn(ORIGINS, mode), 'MODE_INVALID');
  ensure(typeof live === 'boolean' && (!live || mode === 'verify'), 'LIVE_MODE_INVALID');
  validateIds(ids);
  const allowed = new Set(ids.flatMap(id => {
    const base = `/api/public/credentials/${id}`;
    return mode === 'capture' ? [base] : [base, `${base}/certificate`, `${base}/package`];
  }));
  const attempted = new Set();
  return async path => {
    ensure(allowed.has(path) && !attempted.has(path), 'HTTP_SCOPE_REFUSED');
    attempted.add(path);
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 15000);
    try {
      const response = await transport(`${live ? ORIGINS.capture : ORIGINS[mode]}${path}`, {
        method: 'GET', redirect: 'error', credentials: 'omit', signal: controller.signal,
        headers: { Accept: path.endsWith('/certificate') ? 'application/pdf'
          : path.endsWith('/package') ? 'application/zip' : 'application/json' }
      });
      ensure(response.status === 200, 'HTTP_STATUS_NOT_200');
      const binary = path.endsWith('/certificate') || path.endsWith('/package');
      const limit = binary ? 8 * 1024 * 1024 : 262144;
      const reader = response.body?.getReader(); ensure(reader, 'HTTP_BODY_MISSING');
      let length = 0; const chunks = []; let head = Buffer.alloc(0);
      for (;;) {
        const part = await reader.read(); if (part.done) break;
        length += part.value.length;
        if (length > limit) { await reader.cancel(); throw new Error('HTTP_BODY_TOO_LARGE'); }
        if (binary) { if (head.length < 5) head = Buffer.concat([head, part.value]).subarray(0, 5); }
        else chunks.push(part.value);
      }
      if (binary) {
        ensure(path.endsWith('/certificate') ? head.toString('ascii') === '%PDF-'
          : head.subarray(0, 4).equals(Buffer.from([0x50, 0x4b, 0x03, 0x04])), 'BINARY_MAGIC_INVALID');
        return undefined;
      }
      try { return JSON.parse(Buffer.concat(chunks).toString('utf8')); }
      catch { throw new Error('HTTP_JSON_INVALID'); }
    } catch (error) {
      if (/^(HTTP_|BINARY_)/.test(error?.message ?? '')) throw error;
      throw new Error('HTTP_REQUEST_FAILED');
    } finally { clearTimeout(timer); }
  };
}

export async function capture(ids, transport = fetch) {
  const client = makeClient('capture', ids, transport); const records = [];
  for (const id of ids) records.push(projectResponse(await client(`/api/public/credentials/${id}`), id, 'capture'));
  return validateReference({ version: 1, records });
}

export async function verifyReference(reference, transport = fetch, live = false) {
  validateReference(reference);
  const client = makeClient('verify', reference.records.map(record => record.credentialPublicId), transport, live);
  for (const expected of reference.records) {
    const base = `/api/public/credentials/${expected.credentialPublicId}`;
    const actual = projectResponse(await client(base), expected.credentialPublicId, 'verify');
    ensure(JSON.stringify(actual) === JSON.stringify(expected), 'PUBLIC_CONTRACT_MISMATCH');
    await client(`${base}/certificate`); await client(`${base}/package`);
  }
  return { credentials: 6, jsonChecks: 6, binaryChecks: 12, passed: true };
}

export function parseArguments(args) {
  const [mode, ...rest] = args;
  const live = mode === 'verify' && rest.at(-1) === '--live';
  if (live) rest.pop();
  const keys = mode === 'capture' ? ['--ids-file', '--reference-file'] : ['--reference-file'];
  ensure(Object.hasOwn(ORIGINS, mode) && rest.length === keys.length * 2, 'ARGUMENTS_INVALID');
  const values = {};
  keys.forEach((key, index) => {
    ensure(rest[index * 2] === key && isAbsolute(rest[index * 2 + 1]), 'ARGUMENTS_INVALID');
    values[key.slice(2)] = rest[index * 2 + 1];
  });
  return { mode, ...values, live };
}

export async function main(args) {
  const options = parseArguments(args);
  if (options.mode === 'capture') {
    const reference = await capture(validateIds(await privateJson(options['ids-file'])));
    await writeReference(options['reference-file'], reference);
    return { credentials: 6, jsonChecks: 6, captured: true };
  }
  return verifyReference(await privateJson(options['reference-file']), fetch, options.live);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main(process.argv.slice(2)).then(result => console.log(JSON.stringify(result)))
    .catch(() => { console.error('LEGACY_HTTP_GATE_FAILED: no response body or identifier was logged'); process.exitCode = 1; });
}
