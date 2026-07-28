const ADDRESS_PATTERN = /^0x[0-9a-fA-F]{40}$/;
const HASH_PATTERN = /^0x[0-9a-fA-F]{64}$/;
const SIGNATURE_PATTERN = /^0x[0-9a-fA-F]{130}$/;
const UUID_V4_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const KAIA_AMOUNT_PATTERN = /^(?:0|[1-9][0-9]*)(?:\.[0-9]{1,18})?$/;
const WEI_PER_KAIA = 1_000_000_000_000_000_000n;
const MAX_RELAYER_BOOTSTRAP_WEI = 5n * WEI_PER_KAIA;

export function isUuidV4(value) {
  return typeof value === "string" && UUID_V4_PATTERN.test(value);
}

export function parseRelayerTargetBalanceKaia(value) {
  if (typeof value !== "string" || !KAIA_AMOUNT_PATTERN.test(value.trim())) {
    throw new Error("Relayer 목표 잔액은 18자리 이하의 양수 KAIA 수량이어야 합니다.");
  }
  const [whole, fraction = ""] = value.trim().split(".");
  const wei = BigInt(whole) * WEI_PER_KAIA + BigInt(fraction.padEnd(18, "0") || "0");
  if (wei <= 0n || wei > MAX_RELAYER_BOOTSTRAP_WEI) {
    throw new Error("Relayer 목표 잔액은 0보다 크고 5 KAIA 이하여야 합니다.");
  }
  return wei;
}

export function isIssuerKeyActive(key, currentTimestamp) {
  try {
    if (
      key === null ||
      typeof key !== "object" ||
      !ADDRESS_PATTERN.test(key.signer) ||
      /^0x0{40}$/i.test(key.signer)
    ) {
      return false;
    }
    const now = BigInt(currentTimestamp);
    const validFrom = BigInt(key.validFrom);
    const validUntil = BigInt(key.validUntil);
    const compromisedAt = BigInt(key.compromisedAt);
    return now >= 0n
      && validFrom > 0n
      && validFrom <= now
      && validUntil === 0n
      && compromisedAt === 0n;
  } catch {
    return false;
  }
}

export function normalizeRuntimeCode(code, immutableReferences) {
  if (typeof code !== "string" || !/^0x(?:[0-9a-fA-F]{2})+$/.test(code)) {
    throw new Error("Runtime bytecode must be a non-empty even-length hex string.");
  }
  if (!Array.isArray(immutableReferences)) {
    throw new Error("Immutable references must be an array.");
  }

  const bytes = code.slice(2).toLowerCase().split("");
  for (const reference of immutableReferences) {
    const start = reference?.start;
    const length = reference?.length;
    if (
      !Number.isInteger(start) ||
      !Number.isInteger(length) ||
      start < 0 ||
      length < 1 ||
      (start + length) * 2 > bytes.length
    ) {
      throw new Error("Immutable reference is outside runtime bytecode.");
    }
    bytes.fill("0", start * 2, (start + length) * 2);
  }
  return `0x${bytes.join("")}`;
}

export function deploymentStorageKey(deployerAddress) {
  if (!ADDRESS_PATTERN.test(deployerAddress)) {
    throw new Error("Deployer address is invalid.");
  }
  return `trekkey:kairos:registry:v1:${deployerAddress.toLowerCase()}`;
}

export function issuerProofStorageKey(registryAddress, issuerSignerAddress) {
  if (!ADDRESS_PATTERN.test(registryAddress) || !ADDRESS_PATTERN.test(issuerSignerAddress)) {
    throw new Error("Registry or issuer signer address is invalid.");
  }
  return `trekkey:kairos:issuer-proof:v1:${registryAddress.toLowerCase()}:${issuerSignerAddress.toLowerCase()}`;
}

export function parseDeploymentState(serialized, expectedDeployerAddress) {
  if (typeof serialized !== "string" || !ADDRESS_PATTERN.test(expectedDeployerAddress)) {
    return null;
  }
  try {
    const value = JSON.parse(serialized);
    if (
      value?.version !== 1 ||
      value.chainId !== 1001 ||
      !ADDRESS_PATTERN.test(value.deployerAddress) ||
      value.deployerAddress.toLowerCase() !== expectedDeployerAddress.toLowerCase() ||
      !ADDRESS_PATTERN.test(value.registryAddress) ||
      !HASH_PATTERN.test(value.transactionHash) ||
      typeof value.createdAt !== "string"
    ) {
      return null;
    }
    return value;
  } catch {
    return null;
  }
}

export function parseIssuerProof(serialized, registryAddress, issuerSignerAddress) {
  if (
    typeof serialized !== "string" ||
    !ADDRESS_PATTERN.test(registryAddress) ||
    !ADDRESS_PATTERN.test(issuerSignerAddress)
  ) {
    return null;
  }
  try {
    const value = JSON.parse(serialized);
    if (
      value?.version !== 1 ||
      value.chainId !== 1001 ||
      value.registryAddress?.toLowerCase() !== registryAddress.toLowerCase() ||
      value.issuerSignerAddress?.toLowerCase() !== issuerSignerAddress.toLowerCase() ||
      !HASH_PATTERN.test(value.issuerId) ||
      !SIGNATURE_PATTERN.test(value.signature) ||
      typeof value.keyVersion !== "string" ||
      typeof value.createdAt !== "string"
    ) {
      return null;
    }
    return value;
  } catch {
    return null;
  }
}
