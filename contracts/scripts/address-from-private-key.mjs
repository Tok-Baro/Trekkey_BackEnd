import { Wallet } from "ethers";

let input = "";

for await (const chunk of process.stdin) {
  input += chunk;
}

const privateKey = input.trim();
if (!/^0x[0-9a-fA-F]{64}$/.test(privateKey)) {
  throw new Error("Expected one 32-byte 0x-prefixed private key on stdin.");
}

process.stdout.write(`${new Wallet(privateKey).address}\n`);
