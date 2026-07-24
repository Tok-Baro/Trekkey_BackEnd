import { ethers } from "hardhat";

const ISSUER_ID_DOMAIN = ethers.keccak256(
  ethers.toUtf8Bytes("TREKKEY_ISSUER_ID_V1")
);

function required(name: string): string {
  const value = process.env[name]?.trim();
  if (value === undefined || value.length === 0) {
    throw new Error(`${name} is required.`);
  }
  return value;
}

function positiveInteger(name: string): bigint {
  const value = BigInt(required(name));
  if (value <= 0n || value > (1n << 64n) - 1n) {
    throw new Error(`${name} must fit a positive uint64.`);
  }
  return value;
}

function issuerId(publicId: string): string {
  return ethers.keccak256(
    ethers.AbiCoder.defaultAbiCoder().encode(
      ["bytes32", "bytes32"],
      [ISSUER_ID_DOMAIN, ethers.keccak256(ethers.toUtf8Bytes(publicId))]
    )
  );
}

async function main(): Promise<void> {
  const registryAddress = ethers.getAddress(required("REGISTRY_ADDRESS"));
  const signerAddress = ethers.getAddress(required("ISSUER_SIGNER_ADDRESS"));
  const relayerAddress = ethers.getAddress(required("RELAYER_ADDRESS"));
  const publicId = required("ISSUER_PUBLIC_ID");
  const keyVersion = positiveInteger("ISSUER_KEY_VERSION");
  const computedIssuerId = issuerId(publicId);

  const registry = await ethers.getContractAt(
    "TrekkeyCredentialRegistryV1",
    registryAddress
  );

  const relayerRole = await registry.RELAYER_ROLE();
  if (!(await registry.hasRole(relayerRole, relayerAddress))) {
    const grantTransaction = await registry.grantRole(
      relayerRole,
      relayerAddress
    );
    await grantTransaction.wait();
    console.log(`RELAYER_ROLE granted: ${relayerAddress}`);
  } else {
    console.log(`RELAYER_ROLE already granted: ${relayerAddress}`);
  }

  const currentKey = await registry.getIssuerKey(
    computedIssuerId,
    keyVersion
  );
  if (currentKey.signer === ethers.ZeroAddress) {
    const registerTransaction = await registry.registerIssuerKey(
      computedIssuerId,
      keyVersion,
      signerAddress
    );
    await registerTransaction.wait();
    console.log(
      `Issuer key registered: issuerId=${computedIssuerId}, version=${keyVersion}, signer=${signerAddress}`
    );
  } else if (ethers.getAddress(currentKey.signer) !== signerAddress) {
    throw new Error(
      `Issuer key version ${keyVersion} already belongs to ${currentKey.signer}.`
    );
  } else {
    console.log(
      `Issuer key already registered: issuerId=${computedIssuerId}, version=${keyVersion}`
    );
  }
}

main().catch((error: unknown) => {
  console.error(error);
  process.exitCode = 1;
});
