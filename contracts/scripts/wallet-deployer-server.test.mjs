import assert from "node:assert/strict";
import test from "node:test";
import {
  assetFor,
  createWalletDeployerServer,
  isAllowedHost,
  publicConfig,
  runtimeMetadata
} from "./serve-wallet-deployer.mjs";
import {
  deploymentStorageKey,
  isUuidV4,
  normalizeRuntimeCode,
  parseDeploymentState,
  parseIssuerProof
} from "../wallet-deployer/core.mjs";

test("public config exposes only allowlisted non-secret values", () => {
  const config = publicConfig({
    DEPLOYER_ADDRESS: "0x1111111111111111111111111111111111111111",
    DEPLOYER_PRIVATE_KEY: "must-not-leak",
    ISSUER_PRIVATE_KEY: "must-not-leak",
    ISSUER_PUBLIC_ID: "organization-public-id",
    ISSUER_KEY_VERSION: "2",
    ISSUER_SIGNER_ADDRESS: "0x2222222222222222222222222222222222222222",
    RELAYER_ADDRESS: "0x3333333333333333333333333333333333333333"
  });

  assert.equal(config.expectedChainId, 1001);
  assert.equal(config.issuerKeyVersion, "2");
  assert.equal(JSON.stringify(config).includes("must-not-leak"), false);
  assert.equal(Object.hasOwn(config, "DEPLOYER_PRIVATE_KEY"), false);
  assert.equal(Object.hasOwn(config, "ISSUER_PRIVATE_KEY"), false);
});

test("asset allowlist cannot serve env files or parent paths", () => {
  assert.notEqual(assetFor("/"), null);
  assert.notEqual(assetFor("/artifact.json"), null);
  assert.notEqual(assetFor("/core.mjs"), null);
  assert.equal(assetFor("/.env"), null);
  assert.equal(assetFor("/../.env"), null);
  assert.equal(assetFor("/node_modules/dotenv/config.js"), null);
});

test("runtime metadata exposes bounded immutable byte ranges", async () => {
  const metadata = await runtimeMetadata();
  assert.ok(metadata.immutableReferences.length > 0);
  assert.ok(
    metadata.immutableReferences.every(
      ({ start, length }) => Number.isInteger(start) && start >= 0 && Number.isInteger(length) && length > 0
    )
  );
});

test("runtime normalization ignores only declared immutable bytes", () => {
  const code = "0x112233445566";
  assert.equal(
    normalizeRuntimeCode(code, [{ start: 2, length: 2 }]),
    "0x112200005566"
  );
  assert.throws(
    () => normalizeRuntimeCode(code, [{ start: 5, length: 2 }]),
    /outside runtime bytecode/
  );
});

test("organization public ID must be UUIDv4", () => {
  assert.equal(isUuidV4("725050e0-2a2f-48a8-a8b8-2e51e12524b7"), true);
  assert.equal(isUuidV4("organization-public-id"), false);
  assert.equal(isUuidV4("725050e0-2a2f-18a8-a8b8-2e51e12524b7"), false);
});

test("deployment recovery state is fail-closed", () => {
  const deployer = "0x1111111111111111111111111111111111111111";
  const state = {
    version: 1,
    chainId: 1001,
    deployerAddress: deployer,
    registryAddress: "0x2222222222222222222222222222222222222222",
    transactionHash: `0x${"33".repeat(32)}`,
    createdAt: "2026-07-28T00:00:00.000Z"
  };
  assert.equal(deploymentStorageKey(deployer).includes(deployer), true);
  assert.deepEqual(parseDeploymentState(JSON.stringify(state), deployer), state);
  assert.equal(
    parseDeploymentState(JSON.stringify({ ...state, chainId: 8217 }), deployer),
    null
  );
});

test("issuer proof state rejects malformed signatures", () => {
  const registry = "0x1111111111111111111111111111111111111111";
  const signer = "0x2222222222222222222222222222222222222222";
  const proof = {
    version: 1,
    chainId: 1001,
    registryAddress: registry,
    issuerSignerAddress: signer,
    issuerId: `0x${"33".repeat(32)}`,
    keyVersion: "1",
    signature: `0x${"44".repeat(65)}`,
    createdAt: "2026-07-28T00:00:00.000Z"
  };
  assert.deepEqual(parseIssuerProof(JSON.stringify(proof), registry, signer), proof);
  assert.equal(
    parseIssuerProof(JSON.stringify({ ...proof, signature: "0x1234" }), registry, signer),
    null
  );
});

test("server accepts only loopback host headers", () => {
  assert.equal(isAllowedHost("127.0.0.1:4173"), true);
  assert.equal(isAllowedHost("localhost:4173"), true);
  assert.equal(isAllowedHost("evil.example"), false);
  assert.equal(isAllowedHost(undefined), false);
});

test("config endpoint does not return private keys", async (context) => {
  const server = createWalletDeployerServer({
    DEPLOYER_ADDRESS: "0x1111111111111111111111111111111111111111",
    DEPLOYER_PRIVATE_KEY: "secret-deployer-key",
    ISSUER_PRIVATE_KEY: "secret-issuer-key"
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  context.after(() => new Promise((resolve) => server.close(resolve)));

  const address = server.address();
  const response = await fetch(`http://127.0.0.1:${address.port}/config.json`);
  const body = await response.text();

  assert.equal(response.status, 200);
  assert.equal(body.includes("secret-deployer-key"), false);
  assert.equal(body.includes("secret-issuer-key"), false);
});
