import assert from 'node:assert/strict';
import test from 'node:test';
import { EXPECTED_TESTS, parseArguments, setupSql } from './prepare-mysql-suite.mjs';

// Pure synthetic inputs only; never call prepare(), generate a password or connect to MySQL.
test('explicit absence attestation and dedicated root are mandatory', () => {
  assert.equal(EXPECTED_TESTS, 26);
  assert.equal(parseArguments(['--prepare', '--confirmed-absent']).root, '/srv/trekkey-sui-testnet');
  for (const args of [[], ['--prepare'], ['--prepare', '--confirmed-absent', '--root', '/srv'],
    ['--prepare', '--confirmed-absent', '--overwrite']]) assert.throws(() => parseArguments(args));
});
test('generated SQL fails on existing objects and grants only the exact escaped disposable schema', () => {
  const sql = setupSql('ab'.repeat(32));
  assert.match(sql, /CREATE DATABASE `trekkey_test`/);
  assert.match(sql, /CREATE USER 'trekkey_it_suite'@'%'/);
  assert(sql.includes('ON `trekkey\\_test`.*'));
  assert(!sql.includes('IF NOT EXISTS'));
  assert(!/^DROP\s/m.test(sql));
  assert(!sql.includes('trekkey_sui_testnet'));
  assert(!sql.includes('ON *.*'));
  const statements = sql.split('\n').filter(line => !line.startsWith('--')).join('\n').split(';').filter(value => value.trim());
  assert.equal(statements.length, 3);
  for (const value of ['', 'ab', "' OR 1=1", 'a'.repeat(64) + '\n']) assert.throws(() => setupSql(value));
});
