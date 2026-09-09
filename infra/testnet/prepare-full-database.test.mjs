import test from 'node:test';
import assert from 'node:assert/strict';
import { setupSql } from './prepare-full-database.mjs';
test('persistent synthetic schema grants are exact and never destructive', () => {
  const sql = setupSql('a'.repeat(64));
  assert.match(sql, /CREATE DATABASE `trekkey_sui_full`/);
  assert.ok(sql.includes('ON `trekkey\\_sui\\_full`.*'));
  assert.equal((sql.match(/;/g) ?? []).length, 3);
  assert.doesNotMatch(sql, /DROP|IF NOT EXISTS|trekkey_sui_testnet|GRANT OPTION/i);
});
test('SQL cannot accept injected or weak password input', () => {
  for (const value of [null, '', 'secret', "'; DROP DATABASE x; --", 'a'.repeat(63), 'A'.repeat(64)]) {
    assert.throws(() => setupSql(value));
  }
});
