import process from "node:process";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import dotenv from "dotenv";
import {
  AbiCoder,
  Wallet,
  getAddress,
  isAddress,
  keccak256,
  toUtf8Bytes,
  verifyTypedData
} from "ethers";

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
dotenv.config({ path: resolve(scriptDirectory, "..", ".env"), override: false });

const UUID_V4_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const PRIVATE_KEY_PATTERN = /^(?:0x)?[0-9a-fA-F]{64}$/;
const ISSUER_ID_DOMAIN = keccak256(toUtf8Bytes("TREKKEY_ISSUER_ID_V1"));
const STATEMENT_HASH = keccak256(toUtf8Bytes("TREKKEY_ISSUER_KEY_PROOF_V1"));
const TYPES = {
  IssuerKeyProof: [
    { name: "issuerId", type: "bytes32" },
    { name: "keyVersion", type: "uint64" },
    { name: "signer", type: "address" },
    { name: "statementHash", type: "bytes32" }
  ]
};

function requiredEnvironment(name) {
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

async function readPrivateKey() {
  let value = "";
  process.stdin.setEncoding("utf8");
  for await (const chunk of process.stdin) {
    value += chunk;
  }
  const privateKey = value.trim();
  if (!PRIVATE_KEY_PATTERN.test(privateKey)) {
    throw new Error("stdin must contain exactly one 32-byte hex private key");
  }
  return privateKey.startsWith("0x") ? privateKey : `0x${privateKey}`;
}

async function main() {
  const registryAddress = requiredEnvironment("REGISTRY_ADDRESS");
  const publicId = requiredEnvironment("ISSUER_PUBLIC_ID");
  const configuredSigner = requiredEnvironment("ISSUER_SIGNER_ADDRESS");
  const keyVersion = BigInt(requiredEnvironment("ISSUER_KEY_VERSION"));
  if (!isAddress(registryAddress) || !isAddress(configuredSigner)) {
    throw new Error("REGISTRY_ADDRESS and ISSUER_SIGNER_ADDRESS must be EVM addresses");
  }
  if (keyVersion < 1n || keyVersion > (1n << 64n) - 1n) {
    throw new Error("ISSUER_KEY_VERSION must be a positive uint64");
  }

  const wallet = new Wallet(await readPrivateKey());
  if (getAddress(wallet.address) !== getAddress(configuredSigner)) {
    throw new Error("Keychain issuer key does not match ISSUER_SIGNER_ADDRESS");
  }

  const domain = {
    name: "TrekkeyCredentialRegistry",
    version: "1",
    chainId: 1001,
    verifyingContract: getAddress(registryAddress)
  };
  const message = {
    issuerId: issuerId(publicId),
    keyVersion,
    signer: getAddress(configuredSigner),
    statementHash: STATEMENT_HASH
  };
  const signature = await wallet.signTypedData(domain, TYPES, message);
  if (getAddress(verifyTypedData(domain, TYPES, message, signature)) !== wallet.address) {
    throw new Error("Generated issuer proof did not recover the configured signer");
  }
  process.stdout.write(`${signature}\n`);
}

main().catch((error) => {
  process.stderr.write(`${error.message}\n`);
  process.exitCode = 1;
});
