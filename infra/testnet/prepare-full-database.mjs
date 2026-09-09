#!/usr/bin/env node
// Creates a NEW persistent synthetic-test database setup artifact; never executes SQL.
import { randomBytes } from 'node:crypto';
import { lstat, mkdir, open, realpath } from 'node:fs/promises';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { DEFAULT_ROOT, validateRoot, writeExclusive } from './prepare-runtime.mjs';

const ensure = condition => { if (!condition) throw new Error('FULL_DATABASE_PREPARATION_REFUSED'); };
export function setupSql(password) {
  ensure(typeof password === 'string' && /^[0-9a-f]{64}$/.test(password));
  return 'CREATE DATABASE `trekkey_sui_full` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;\n'
    + `CREATE USER 'trekkey_sui_full'@'%' IDENTIFIED BY '${password}';\n`
    + "GRANT ALL PRIVILEGES ON `trekkey\\_sui\\_full`.* TO 'trekkey_sui_full'@'%';\n";
}
async function checkedDirectory(path, uid, mode) {
  const stat = await lstat(path);
  ensure(stat.isDirectory() && !stat.isSymbolicLink() && stat.uid === uid
    && (mode === undefined ? (stat.mode & 0o022) === 0 : (stat.mode & 0o777) === mode)
    && await realpath(path) === path);
}
export async function prepare(root) {
  ensure(process.getuid?.() === 0);
  validateRoot(root);
  await checkedDirectory(root, 0);
  await checkedDirectory(join(root, 'secrets'), 0, 0o700);
  process.umask(0o077);
  const target = join(root, 'secrets/full');
  // Existing or partially prepared state is refused, not automatically replaced.
  await mkdir(target, { mode: 0o700 });
  const parent = await open(join(root, 'secrets'), 'r');
  try { await parent.sync(); } finally { await parent.close(); }
  await writeExclusive(join(target, 'preflight-absent.claim'),
    'Operator confirmed trekkey_sui_full schema/user absent on isolated MySQL port 13306. SQL not yet executed.\n', 0);
  const entropy = randomBytes(32);
  let password;
  try { password = entropy.toString('hex'); } finally { entropy.fill(0); }
  try {
    await writeExclusive(join(target, 'DATASOURCE_PASSWORD'), password, 10001);
    await writeExclusive(join(target, 'setup.sql'), setupSql(password), 0);
    const loginEntropy = randomBytes(32);
    try { await writeExclusive(join(target, 'E2E_LOGIN_PASSWORD'), loginEntropy.toString('hex'), 10001); }
    finally { loginEntropy.fill(0); }
  } finally { password = ''; }
  for (const name of ['uploads-full', 'full-e2e']) {
    const path = join(root, name);
    await mkdir(path, { mode: 0o700 });
    const directory = await open(path, 'r');
    try { await directory.chown(10001, 10001); await directory.chmod(0o700); await directory.sync(); }
    finally { await directory.close(); }
  }
  return { prepared: true, sqlExecuted: false, schema: 'trekkey_sui_full', secretValuesPrinted: false };
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  Promise.resolve().then(() => {
    const args = process.argv.slice(2);
    ensure(args[0] === '--prepare' && args[1] === '--confirmed-absent'
      && (args.length === 2 || (args.length === 4 && args[2] === '--root')));
    return prepare(args.length === 4 ? args[3] : DEFAULT_ROOT);
  }).then(result => process.stdout.write(`${JSON.stringify(result)}\n`)).catch(() => {
    process.stderr.write('Full database preparation refused or incomplete. Preserve partial files and verify scope/permissions; no SQL was executed.\n');
    process.exitCode = 1;
  });
}
