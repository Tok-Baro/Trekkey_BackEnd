#!/usr/bin/env node
// Explicit operator utility. Importing this module never creates files or secrets.
import { randomBytes } from 'node:crypto';
import { constants } from 'node:fs';
import { lstat, mkdir, open, realpath } from 'node:fs/promises';
import { basename, dirname, isAbsolute, join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

export const DEFAULT_ROOT = '/srv/trekkey-sui-testnet';
const fail = () => { throw new Error('RUNTIME_PREPARATION_REFUSED'); };
const ensure = condition => { if (!condition) fail(); };
const missing = error => error?.code === 'ENOENT';

export function validateRoot(value) {
  ensure(typeof value === 'string' && isAbsolute(value) && resolve(value) === value &&
    /^\/[A-Za-z0-9._/-]+$/.test(value) &&
    /^trekkey-sui-testnet(?:[-.][A-Za-z0-9.-]+)?$/.test(basename(value)) && dirname(value) !== '/');
  return value;
}
export function parseArguments(args) {
  const [mode, ...rest] = args;
  ensure(mode === '--init-secrets' || mode === '--public-env' || mode === '--add-evidence-secret');
  ensure(rest.length === 0 || (rest.length === 2 && rest[0] === '--root'));
  return { mode, root: validateRoot(rest.length ? rest[1] : DEFAULT_ROOT) };
}
async function syncDirectory(path) {
  const fd = await open(path, constants.O_RDONLY | constants.O_NOFOLLOW);
  try { await fd.sync(); } finally { await fd.close(); }
}
async function inspectDirectory(path, uid, mode) {
  const stat = await lstat(path);
  ensure(stat.isDirectory() && !stat.isSymbolicLink() && stat.uid === uid &&
    (mode === undefined ? (stat.mode & 0o022) === 0 : (stat.mode & 0o777) === mode) &&
    await realpath(path) === path);
}
async function directory(path, uid, mode) {
  // Parent paths are checked before creation. Never chown/chmod existing files.
  try { await lstat(path); }
  catch (error) {
    if (!missing(error)) throw error;
    ensure(await realpath(dirname(path)) === dirname(path));
    await mkdir(path, { mode });
    const fd = await open(path, constants.O_RDONLY | constants.O_NOFOLLOW);
    try { await fd.chown(uid, uid); await fd.chmod(mode); await fd.sync(); }
    finally { await fd.close(); }
    await syncDirectory(dirname(path));
  }
  await inspectDirectory(path, uid, mode);
}
async function absent(path) {
  try { await lstat(path); } catch (error) { if (missing(error)) return; throw error; }
  fail();
}

// Used by the CLI only after root/scope checks; exported for synthetic fixture tests.
export async function writeExclusive(path, contents, uid, mode = 0o600) {
  ensure(typeof contents === 'string' && contents.length > 0 && contents.length <= 65536);
  const file = await open(path, constants.O_WRONLY | constants.O_CREAT | constants.O_EXCL | constants.O_NOFOLLOW, 0o600);
  try {
    if ((await file.stat()).uid !== uid) await file.chown(uid, uid);
    await file.chmod(mode);
    await file.writeFile(contents, 'utf8');
    await file.sync();
  } finally { await file.close(); }
  await syncDirectory(dirname(path));
}

export function secretRecords(entropy = randomBytes) {
  const encode = (size, encoding) => {
    const buffer = entropy(size);
    ensure(Buffer.isBuffer(buffer) && buffer.length === size);
    try { return buffer.toString(encoding); } finally { buffer.fill(0); }
  };
  const records = [
    { path: 'secrets/gateway/gateway-token', uid: 10001, value: encode(48, 'hex') },
    { path: 'secrets/backend/JWT_SECRET_KEY', uid: 10001, value: encode(64, 'base64') },
    { path: 'secrets/mysql/DATASOURCE_PASSWORD', uid: 10001, value: encode(32, 'hex') },
    { path: 'secrets/mysql/MYSQL_ROOT_PASSWORD', uid: 0, value: encode(32, 'hex') },
  ];
  ensure(new Set(records.map(record => record.value)).size === records.length);
  return records;
}

export function evidenceSecret(entropy = randomBytes) {
  const buffer = entropy(32);
  ensure(Buffer.isBuffer(buffer) && buffer.length === 32);
  try { return buffer.toString('hex'); } finally { buffer.fill(0); }
}

function base58Bytes(value) {
  const alphabet = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
  ensure(typeof value === 'string' && /^[1-9A-HJ-NP-Za-km-z]{32,44}$/.test(value));
  let number = 0n;
  for (const char of value) number = number * 58n + BigInt(alphabet.indexOf(char));
  const output = [];
  while (number > 0n) { output.push(Number(number & 255n)); number >>= 8n; }
  output.reverse();
  for (const char of value) { if (char !== '1') break; output.unshift(0); }
  ensure(output.length === 32);
  return Buffer.from(output);
}
export function publicEnvironment(root, manifest) {
  validateRoot(root);
  ensure(manifest && typeof manifest === 'object' && !Array.isArray(manifest) &&
    manifest.network === 'testnet' && manifest.protocolVersion === 1 && manifest.synthetic === true);
  ensure(typeof manifest.chainIdentifier === 'string' && /^[0-9a-f]{8}$/.test(manifest.chainIdentifier));
  ensure(base58Bytes(manifest.genesisDigest).subarray(0, 4).toString('hex') === manifest.chainIdentifier);
  for (const field of ['packageId', 'registryId', 'relayerAddress']) {
    ensure(typeof manifest[field] === 'string' && /^0x[0-9a-f]{64}$/.test(manifest[field]) && !/^0x0{64}$/.test(manifest[field]));
  }
  ensure(/^[0-9a-f]{64}$/.test(manifest.bytecodeSha256 ?? '') && manifest.transactions && typeof manifest.transactions === 'object');
  for (const phase of ['publish', 'registry', 'authorize']) base58Bytes(manifest.transactions[phase]);
  // Explicit allowlist: never serialize arbitrary manifest properties or secrets.
  return `TESTNET_ROOT=${root}\nSUI_CHAIN_IDENTIFIER=${manifest.chainIdentifier}\nSUI_PACKAGE_ID=${manifest.packageId}\nSUI_REGISTRY_ID=${manifest.registryId}\n`;
}

export async function prepareRuntime(mode, inputRoot) {
  ensure(process.getuid?.() === 0); // Both modes require root-owned output scope.
  const root = validateRoot(inputRoot);
  await inspectDirectory(root, 0); // Parent creates this dedicated directory first.
  process.umask(0o077);
  if (mode === '--add-evidence-secret') {
    await inspectDirectory(join(root, 'secrets'), 0, 0o700);
    await inspectDirectory(join(root, 'secrets/backend'), 10001, 0o700);
    const target = join(root, 'secrets/backend/EVIDENCE_LOOKUP_HMAC_SECRET');
    await absent(target);
    // Does not inspect, replace or regenerate the existing four runtime secrets.
    await writeExclusive(target, evidenceSecret(), 10001);
    return { mode, createdFiles: 1, secretValuesPrinted: false };
  }
  if (mode === '--init-secrets') {
    await directory(join(root, 'secrets'), 0, 0o700);
    for (const name of ['deployment', 'gateway', 'backend']) await directory(join(root, 'secrets', name), 10001, 0o700);
    await directory(join(root, 'secrets/mysql'), 0, 0o700);
    await directory(join(root, 'journal'), 10001, 0o700);
    await directory(join(root, 'uploads'), 10001, 0o700);
    const targets = ['secrets/gateway/gateway-token', 'secrets/backend/JWT_SECRET_KEY',
      'secrets/mysql/DATASOURCE_PASSWORD', 'secrets/mysql/MYSQL_ROOT_PASSWORD'];
    for (const target of targets) await absent(join(root, target));
    await writeExclusive(join(root, 'secrets/runtime-init.claim'), 'Runtime initialization claimed; never regenerate after partial failure.\n', 0);
    const records = secretRecords();
    try {
      for (const record of records) await writeExclusive(join(root, record.path), record.value, record.uid);
    } finally { for (const record of records) record.value = ''; }
    return { mode, createdFiles: 4, secretValuesPrinted: false };
  }
  ensure(mode === '--public-env');
  await inspectDirectory(join(root, 'secrets'), 0, 0o700);
  await inspectDirectory(join(root, 'secrets/deployment'), 10001, 0o700);
  const fd = await open(join(root, 'secrets/deployment/deployment.json'), constants.O_RDONLY | constants.O_NOFOLLOW);
  let manifest;
  try {
    const stat = await fd.stat();
    ensure(stat.isFile() && stat.nlink === 1 && stat.uid === 10001 && (stat.mode & 0o777) === 0o600 && stat.size > 0 && stat.size <= 65536);
    manifest = JSON.parse(await fd.readFile('utf8'));
  } finally { await fd.close(); }
  const contents = publicEnvironment(root, manifest);
  await writeExclusive(join(root, 'deploy.env'), contents, 0, 0o644);
  return { mode, createdFiles: 1, secretValuesPrinted: false };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  Promise.resolve().then(() => {
    const { mode, root } = parseArguments(process.argv.slice(2));
    return prepareRuntime(mode, root);
  }).then(result => process.stdout.write(`${JSON.stringify(result)}\n`)).catch(() => {
    process.stderr.write('Runtime preparation refused or incomplete. Check dedicated scope, owner/mode and existing files. Preserve partial files and claim; do not regenerate secrets.\n');
    process.exitCode = 1;
  });
}
