import { readFile } from "node:fs/promises";
import { ethers } from "hardhat";

type TypedDataDocument = {
  domain: Record<string, unknown>;
  types: Record<string, Array<{ name: string; type: string }>>;
  primaryType: string;
  message: Record<string, unknown>;
};

function required(name: string): string {
  const value = process.env[name]?.trim();
  if (value === undefined || value.length === 0) {
    throw new Error(`${name} is required.`);
  }
  return value;
}

async function main(): Promise<void> {
  const document = JSON.parse(
    await readFile(required("TYPED_DATA_FILE"), "utf8")
  ) as TypedDataDocument;
  const privateKey = required("ISSUER_PRIVATE_KEY");
  const wallet = new ethers.Wallet(privateKey);
  const messageTypes = Object.fromEntries(
    Object.entries(document.types).filter(([name]) => name !== "EIP712Domain")
  );

  if (messageTypes[document.primaryType] === undefined) {
    throw new Error(`Primary type ${document.primaryType} is not declared.`);
  }

  const digest = ethers.TypedDataEncoder.hash(
    document.domain,
    messageTypes,
    document.message
  );
  const signature = await wallet.signTypedData(
    document.domain,
    messageTypes,
    document.message
  );

  console.log(`Signer: ${wallet.address}`);
  console.log(`Digest: ${digest}`);
  console.log(`Signature: ${signature}`);
}

main().catch((error: unknown) => {
  console.error(error);
  process.exitCode = 1;
});
