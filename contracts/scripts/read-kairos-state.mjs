import { readFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import process from "node:process";
import dotenv from "dotenv";
import { isIssuerKeyActive } from "../wallet-deployer/core.mjs";
import {
  AbiCoder,
  Contract,
  JsonRpcProvider,
  ZeroAddress,
  formatEther,
  getAddress,
  isAddress,
  keccak256,
  toUtf8Bytes
} from "ethers";

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const contractsDirectory = resolve(scriptDirectory, "..");
dotenv.config({ path: resolve(contractsDirectory, ".env"), override: false });

const ISSUER_ID_DOMAIN = keccak256(toUtf8Bytes("TREKKEY_ISSUER_ID_V1"));
const EXPECTED_LEAF_DOMAIN = keccak256(toUtf8Bytes("TREKKEY_CREDENTIAL_LEAF_V1"));
const UUID_V4_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function required(name) {
  const value = process.env[name]?.trim();
  if (!value) {
    throw new Error(`${name} is required`);
  }
  return value;
}

function issuerId(publicId) {
  if (!UUID_V4_PATTERN.test(publicId)) {
    throw new Error("ISSUER_PUBLIC_ID must be a UUIDv4");
  }
  return keccak256(
    AbiCoder.defaultAbiCoder().encode(
      ["bytes32", "bytes32"],
      [ISSUER_ID_DOMAIN, keccak256(toUtf8Bytes(publicId))]
    )
  );
}

async function main() {
  const deployment = JSON.parse(await readFile(
    resolve(contractsDirectory, "deployments/kairos-1001.json"),
    "utf8"
  ));
  const rpcUrl = process.env.KAIROS_RPC_URL?.trim()
    || "https://public-en-kairos.node.kaia.io";
  const registryAddress = required("REGISTRY_ADDRESS");
  const deployerAddress = required("DEPLOYER_ADDRESS");
  const issuerPublicId = required("ISSUER_PUBLIC_ID");
  const issuerKeyVersion = BigInt(required("ISSUER_KEY_VERSION"));
  const expectedIssuerSigner = required("ISSUER_SIGNER_ADDRESS");
  const relayerAddress = required("RELAYER_ADDRESS");
  for (const [label, address] of [
    ["REGISTRY_ADDRESS", registryAddress],
    ["DEPLOYER_ADDRESS", deployerAddress],
    ["ISSUER_SIGNER_ADDRESS", expectedIssuerSigner],
    ["RELAYER_ADDRESS", relayerAddress]
  ]) {
    if (!isAddress(address) || getAddress(address) === ZeroAddress) {
      throw new Error(`${label} must be a nonzero EVM address`);
    }
  }
  if (issuerKeyVersion < 1n || issuerKeyVersion > (1n << 64n) - 1n) {
    throw new Error("ISSUER_KEY_VERSION must be a positive uint64");
  }

  const artifact = JSON.parse(await readFile(
    resolve(
      contractsDirectory,
      "artifacts/contracts/TrekkeyCredentialRegistryV1.sol/TrekkeyCredentialRegistryV1.json"
    ),
    "utf8"
  ));
  const provider = new JsonRpcProvider(rpcUrl);
  const network = await provider.getNetwork();
  if (network.chainId !== 1001n) {
    throw new Error(`RPC chainId ${network.chainId} is not Kairos 1001`);
  }
  const contract = new Contract(registryAddress, artifact.abi, provider);
  const code = await provider.getCode(registryAddress);
  const latestBlock = await provider.getBlock("latest");
  if (latestBlock === null) {
    throw new Error("Kairos latest block is unavailable");
  }
  const defaultAdminRole = await contract.DEFAULT_ADMIN_ROLE();
  const issuerAdminRole = await contract.ISSUER_KEY_ADMIN_ROLE();
  const relayerRole = await contract.RELAYER_ROLE();
  const currentIssuer = await contract.getIssuerKey(
    issuerId(issuerPublicId),
    issuerKeyVersion
  );
  const currentIssuerSigner = getAddress(currentIssuer.signer);
  const currentTimestamp = BigInt(latestBlock.timestamp);
  const runtimeCodeBytes = code === "0x" ? 0 : (code.length - 2) / 2;
  const runtimeCodeHash = code === "0x" ? null : keccak256(code);
  const checks = {
    registryAddress: getAddress(registryAddress) === getAddress(deployment.address),
    deployerAddress: getAddress(deployerAddress) === getAddress(deployment.deployer),
    issuerPublicId: issuerPublicId === deployment.configuration.issuerPublicId,
    issuerKeyVersion: issuerKeyVersion === BigInt(deployment.configuration.issuerKeyVersion),
    relayerAddress: getAddress(relayerAddress) === getAddress(deployment.configuration.relayer),
    contractCode: code !== "0x"
      && runtimeCodeBytes === deployment.runtimeCodeBytes
      && runtimeCodeHash === deployment.runtimeCodeHash,
    leafDomain: await contract.LEAF_DOMAIN() === EXPECTED_LEAF_DOMAIN,
    defaultAdmin: await contract.hasRole(defaultAdminRole, deployerAddress),
    issuerKeyAdmin: await contract.hasRole(issuerAdminRole, deployerAddress),
    relayerRole: await contract.hasRole(relayerRole, relayerAddress),
    issuerSigner: currentIssuerSigner === getAddress(expectedIssuerSigner)
      && currentIssuerSigner === getAddress(deployment.configuration.issuerSigner),
    issuerActive: isIssuerKeyActive(currentIssuer, currentTimestamp)
  };
  const output = {
    chainId: Number(network.chainId),
    registryAddress: getAddress(registryAddress),
    issuerPublicId,
    issuerId: issuerId(issuerPublicId),
    issuerKeyVersion: issuerKeyVersion.toString(),
    issuerSigner: currentIssuerSigner,
    issuerValidFrom: currentIssuer.validFrom.toString(),
    issuerValidUntil: currentIssuer.validUntil.toString(),
    issuerCompromisedAt: currentIssuer.compromisedAt.toString(),
    relayerAddress: getAddress(relayerAddress),
    relayerBalanceKaia: formatEther(await provider.getBalance(relayerAddress)),
    runtimeCodeBytes,
    runtimeCodeHash,
    leafDomain: await contract.LEAF_DOMAIN(),
    checks
  };
  process.stdout.write(`${JSON.stringify(output, null, 2)}\n`);
  if (Object.values(checks).some((passed) => !passed)) {
    process.exitCode = 1;
  }
  provider.destroy();
}

main().catch((error) => {
  process.stderr.write(`${error.message}\n`);
  process.exitCode = 1;
});
