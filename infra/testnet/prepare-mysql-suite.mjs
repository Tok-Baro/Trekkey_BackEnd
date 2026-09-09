#!/usr/bin/env node
// Generates protected local artifacts only. No database client, network or shell execution.
import { randomBytes } from 'node:crypto';
import { constants } from 'node:fs';
import { lstat, mkdir, open, realpath } from 'node:fs/promises';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { DEFAULT_ROOT, validateRoot, writeExclusive } from './prepare-runtime.mjs';

export const SCHEMA = 'trekkey_test';
export const USER = 'trekkey_it_suite';
export const EXPECTED_TESTS = 26;
const ensure = value => { if (!value) throw new Error('MYSQL_SUITE_PREPARATION_REFUSED'); };
export function parseArguments(args) {
  ensure(args[0] === '--prepare' && args[1] === '--confirmed-absent');
  ensure(args.length === 2 || (args.length === 4 && args[2] === '--root'));
  return { root: validateRoot(args.length === 4 ? args[3] : DEFAULT_ROOT) };
}
export function setupSql(password) {
  ensure(typeof password === 'string' && /^[0-9a-f]{64}$/.test(password));
  // Run only in the already verified isolated MySQL container, batch mode, without --force.
  // CREATE failures MUST stop execution. Never use this against an existing schema or user.
  return '-- Disposable suite ONLY. Stop on first error; never use --force. No DROP statements.\n'
    + 'CREATE DATABASE `trekkey_test` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;\n'
    + `CREATE USER 'trekkey_it_suite'@'%' IDENTIFIED BY '${password}';\n`
    + "GRANT ALL PRIVILEGES ON `trekkey\\_test`.* TO 'trekkey_it_suite'@'%';\n";
}
async function directory(path, exactMode) {
  const stat = await lstat(path);
  ensure(stat.isDirectory() && !stat.isSymbolicLink() && stat.uid === 0 &&
    (exactMode === undefined ? (stat.mode & 0o022) === 0 : (stat.mode & 0o777) === exactMode)
    && await realpath(path) === path);
}
export async function prepare(root) {
  ensure(process.getuid?.() === 0);
  validateRoot(root);
  await directory(root);
  await directory(join(root, 'secrets'), 0o700);
  process.umask(0o077);
  const target = join(root, 'secrets/mysql-suite');
  // Directory creation is itself exclusive: a failed/previous run is never resumed automatically.
  await mkdir(target, { mode: 0o700 });
  await directory(target, 0o700);
  const parent = await open(join(root, 'secrets'), constants.O_RDONLY | constants.O_NOFOLLOW);
  try { await parent.sync(); } finally { await parent.close(); }
  await writeExclusive(join(target, 'preflight-absent.claim'),
    'Operator confirmed schema trekkey_test and user trekkey_it_suite absent on isolated MySQL port 13306. SQL has not been executed by this helper.\n', 0);
  const bytes = randomBytes(32);
  let password;
  try { password = bytes.toString('hex'); } finally { bytes.fill(0); }
  try {
    await writeExclusive(join(target, 'MYSQL_TEST_PASSWORD'), password, 10001);
    await writeExclusive(join(target, 'setup.sql'), setupSql(password), 0);
    await writeExclusive(join(target, 'connection-public.json'), JSON.stringify({
      schema: SCHEMA, username: USER, host: '127.0.0.1', port: 13306,
      disposable: true, expectedTests: EXPECTED_TESTS, sqlExecuted: false,
    }), 0);
  } finally { password = ''; }
  return { prepared: true, sqlExecuted: false, expectedTests: EXPECTED_TESTS, secretValuesPrinted: false };
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  Promise.resolve().then(() => prepare(parseArguments(process.argv.slice(2)).root))
    .then(result => process.stdout.write(`${JSON.stringify(result)}\n`)).catch(() => {
      process.stderr.write('MySQL suite preparation refused or incomplete. Preserve existing artifacts; confirm isolated server/schema/user and permissions. No SQL was executed.\n');
      process.exitCode = 1;
    });
}
