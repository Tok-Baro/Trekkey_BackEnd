import assert from 'node:assert/strict';
import { once } from 'node:events';
import test from 'node:test';
import { authorized, createGatewayServer } from '../src/server.js';
import { readConfig } from '../src/config.js';
import { identity, setup } from './fixtures.js';

const token = 'public-synthetic-test-token-32-characters';
test('constant-time authentication rejects absent, incorrect and wrong-scheme credentials', () => {
  assert(authorized(`Bearer ${token}`, token));
  for (const value of [undefined, '', 'Bearer wrong', `Basic ${token}`, `Bearer ${token}x`]) assert(!authorized(value, token));
});
test('configuration requires strong secret, canonical identity, loopback bind and safe network/RPC', () => {
  const env = { SUI_GATEWAY_TOKEN: token, SUI_CHAIN_IDENTIFIER: identity.chainIdentifier, SUI_PACKAGE_ID: identity.packageId,
    SUI_REGISTRY_ID: identity.registryId, SUI_RELAYER_PRIVATE_KEY: 'suiprivkey-synthetic-not-loaded', SUI_GATEWAY_JOURNAL_DIR: '/private/tmp/trekkey-config-test-not-created' };
  assert.equal(readConfig(env).bind, '127.0.0.1'); assert.equal(readConfig(env).port, 9187);
  for (const override of [{ SUI_GATEWAY_TOKEN: '' }, { SUI_GATEWAY_BIND: '0.0.0.0' }, { SUI_CHAIN_IDENTIFIER: '123' },
    { SUI_GRPC_URL: 'http://example.com' }, { SUI_GRPC_URL: 'https://secret:password@example.com' },
    { SUI_NETWORK: 'localnet', SUI_GRPC_URL: 'https://fullnode.mainnet.sui.io' }, { SUI_GAS_BUDGET: '1000000001' }]) {
    assert.throws(() => readConfig({ ...env, ...override }));
  }
});
test('local HTTP envelope enforces bearer, rejects browser origin and does not leak RPC errors', async () => {
  const { gateway, chain } = setup(), server = createGatewayServer(gateway, token);
  server.listen(0, '127.0.0.1'); await once(server, 'listening');
  const address = server.address(); assert(address && typeof address !== 'string');
  const base = `http://127.0.0.1:${address.port}`;
  try {
    let response = await fetch(`${base}/v1/identity`);
    assert.equal(response.status, 401); assert.equal((await response.json()).ok, false);
    response = await fetch(`${base}/v1/identity`, { headers: { Authorization: `Bearer ${token}` } });
    assert.equal(response.status, 200); assert.deepEqual((await response.json()).data, identity);
    assert.equal(response.headers.get('cache-control'), 'no-store');
    response = await fetch(`${base}/v1/identity`, { headers: { Authorization: `Bearer ${token}`, Origin: 'https://attacker.invalid' } });
    assert.equal(response.status, 403);
    chain.fieldFailure = new Error(`SECRET-MUST-NOT-LEAK-${token}`);
    response = await fetch(`${base}/v1/batch`, { method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ batchIdHash: identity.packageId }) });
    assert.equal(response.status, 502);
    const output = await response.text(); assert(!output.includes(token)); assert(!output.includes('SECRET-MUST-NOT-LEAK'));
    assert.equal(JSON.parse(output).error.retryable, true);
  } finally { server.closeAllConnections(); await new Promise<void>(resolve => server.close(() => resolve())); }
});
