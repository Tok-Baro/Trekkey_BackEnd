import { ethers } from "hardhat";

async function main(): Promise<void> {
  const [deployer] = await ethers.getSigners();
  if (deployer === undefined) {
    throw new Error("DEPLOYER_PRIVATE_KEY is required for Kairos deployment.");
  }

  const registryFactory = await ethers.getContractFactory("TrekkeyCredentialRegistryV1");
  const registry = await registryFactory.deploy(deployer.address);
  await registry.waitForDeployment();

  const address = await registry.getAddress();
  console.log(`TrekkeyCredentialRegistryV1 deployed: ${address}`);
  console.log(`Initial admin: ${deployer.address}`);
  console.log(`Verify: npm run verify:kairos -- ${address} ${deployer.address}`);
}

main().catch((error: unknown) => {
  console.error(error);
  process.exitCode = 1;
});
