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
  isIssuerKeyActive,
  isUuidV4,
  normalizeRuntimeCode,
  parseDeploymentState,
  parseIssuerProof,
  parseRelayerTargetBalanceKaia
} from "../wallet-deployer/core.mjs";
import {
  AbiCoder,
  Wallet,
  getAddress,
  keccak256,
  toUtf8Bytes,
  verifyTypedData
} from "ethers";
import { spawnSync } from "node:child_process";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDirectory = dirname(fileURLToPath(import.meta.url));

test("public config exposes only allowlisted non-secret values", () => {
  const config = publicConfig({
    DEPLOYER_ADDRESS: "0x1111111111111111111111111111111111111111",
    DEPLOYER_PRIVATE_KEY: "must-not-leak",
    ISSUER_PRIVATE_KEY: "must-not-leak",
    RELAYER_PRIVATE_KEY: "must-not-leak",
    ISSUER_PUBLIC_ID: "organization-public-id",
    ISSUER_KEY_VERSION: "2",
    ISSUER_SIGNER_ADDRESS: "0x2222222222222222222222222222222222222222",
    ISSUER_PROOF_SIGNATURE: `0x${"44".repeat(65)}`,
    RELAYER_ADDRESS: "0x3333333333333333333333333333333333333333",
    RELAYER_TARGET_BALANCE_KAIA: "2.5"
  });

  assert.equal(config.expectedChainId, 1001);
  assert.equal(config.issuerKeyVersion, "2");
  assert.equal(config.issuerProofSignature, `0x${"44".repeat(65)}`);
  assert.equal(config.relayerTargetBalanceKaia, "2.5");
  assert.equal(JSON.stringify(config).includes("must-not-leak"), false);
  assert.equal(Object.hasOwn(config, "DEPLOYER_PRIVATE_KEY"), false);
  assert.equal(Object.hasOwn(config, "ISSUER_PRIVATE_KEY"), false);
  assert.equal(Object.hasOwn(config, "RELAYER_PRIVATE_KEY"), false);
});

test("public config rejects malformed issuer proof signatures", () => {
  assert.throws(
    () => publicConfig({ ISSUER_PROOF_SIGNATURE: "0x1234" }),
    /65-byte hex signature/
  );
});

test("issuer proof helper signs stdin key without printing it", () => {
  const privateKey = `0x${"11".repeat(32)}`;
  const wallet = new Wallet(privateKey);
  const registryAddress = "0x1111111111111111111111111111111111111111";
  const publicId = "725050e0-2a2f-48a8-a8b8-2e51e12524b7";
  const result = spawnSync(
    process.execPath,
    [resolve(scriptDirectory, "sign-issuer-proof-from-stdin.mjs")],
    {
      input: `${privateKey}\n`,
      encoding: "utf8",
      env: {
        ...process.env,
        REGISTRY_ADDRESS: registryAddress,
        ISSUER_PUBLIC_ID: publicId,
        ISSUER_KEY_VERSION: "2",
        ISSUER_SIGNER_ADDRESS: wallet.address
      }
    }
  );

  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.stdout.includes(privateKey), false);
  const signature = result.stdout.trim();
  assert.match(signature, /^0x[0-9a-fA-F]{130}$/);

  const issuerIdDomain = keccak256(toUtf8Bytes("TREKKEY_ISSUER_ID_V1"));
  const issuerId = keccak256(
    AbiCoder.defaultAbiCoder().encode(
      ["bytes32", "bytes32"],
      [issuerIdDomain, keccak256(toUtf8Bytes(publicId))]
    )
  );
  const recovered = verifyTypedData(
    {
      name: "TrekkeyCredentialRegistry",
      version: "1",
      chainId: 1001,
      verifyingContract: registryAddress
    },
    {
      IssuerKeyProof: [
        { name: "issuerId", type: "bytes32" },
        { name: "keyVersion", type: "uint64" },
        { name: "signer", type: "address" },
        { name: "statementHash", type: "bytes32" }
      ]
    },
    {
      issuerId,
      keyVersion: 2n,
      signer: wallet.address,
      statementHash: keccak256(toUtf8Bytes("TREKKEY_ISSUER_KEY_PROOF_V1"))
    },
    signature
  );
  assert.equal(getAddress(recovered), getAddress(wallet.address));
});

test("Keychain helper has no command that prints a private key", () => {
  for (const command of ["issuer-key", "relayer-key"]) {
    const result = spawnSync(
      resolve(scriptDirectory, "kairos-e2e-keychain.sh"),
      [command],
      {
        encoding: "utf8",
        env: { ...process.env, USER: process.env.USER || "trekkey-test" }
      }
    );
    assert.equal(result.status, 2);
    assert.equal(result.stdout, "");
    assert.match(result.stderr, /Usage:/);
  }
});

test("relayer target defaults to one public KAIA value", () => {
  assert.equal(
    publicConfig({}).relayerTargetBalanceKaia,
    "1"
  );
  assert.equal(parseRelayerTargetBalanceKaia("1"), 1_000_000_000_000_000_000n);
  assert.equal(parseRelayerTargetBalanceKaia("2.500000000000000000"), 2_500_000_000_000_000_000n);
  assert.throws(() => parseRelayerTargetBalanceKaia("0"), /0보다 크고 5 KAIA 이하여야/);
  assert.throws(() => parseRelayerTargetBalanceKaia("5.000000000000000001"), /0보다 크고 5 KAIA 이하여야/);
  assert.throws(() => parseRelayerTargetBalanceKaia("6"), /0보다 크고 5 KAIA 이하여야/);
  assert.throws(() => parseRelayerTargetBalanceKaia("1.1234567890123456789"), /18자리 이하/);
});

test("issuer key activity rejects missing, future, retired, and compromised keys", () => {
  const active = {
    signer: "0x1111111111111111111111111111111111111111",
    validFrom: 100n,
    validUntil: 0n,
    compromisedAt: 0n
  };
  assert.equal(isIssuerKeyActive(active, 100n), true);
  assert.equal(isIssuerKeyActive({ ...active, validFrom: 101n }, 100n), false);
  assert.equal(isIssuerKeyActive({ ...active, validUntil: 200n }, 100n), false);
  assert.equal(isIssuerKeyActive({ ...active, compromisedAt: 100n }, 100n), false);
  assert.equal(isIssuerKeyActive({ ...active, signer: "0x0000000000000000000000000000000000000000" }, 100n), false);
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
    ISSUER_PRIVATE_KEY: "secret-issuer-key",
    RELAYER_PRIVATE_KEY: "secret-relayer-key"
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  context.after(() => new Promise((resolve) => server.close(resolve)));

  const address = server.address();
  const response = await fetch(`http://127.0.0.1:${address.port}/config.json`);
  const body = await response.text();

  assert.equal(response.status, 200);
  assert.equal(body.includes("secret-deployer-key"), false);
  assert.equal(body.includes("secret-issuer-key"), false);
  assert.equal(body.includes("secret-relayer-key"), false);
});
