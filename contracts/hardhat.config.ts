import "@nomicfoundation/hardhat-toolbox";
import "dotenv/config";
import type { HardhatUserConfig } from "hardhat/config";

const kairosRpcUrl =
  process.env.KAIROS_RPC_URL ?? "https://public-en-kairos.node.kaia.io";
const deployerPrivateKey = process.env.DEPLOYER_PRIVATE_KEY?.trim();

const config: HardhatUserConfig = {
  solidity: {
    version: "0.8.28",
    settings: {
      evmVersion: "london",
      optimizer: {
        enabled: true,
        runs: 200
      }
    }
  },
  networks: {
    kairos: {
      chainId: 1001,
      url: kairosRpcUrl,
      accounts:
        deployerPrivateKey === undefined || deployerPrivateKey.length === 0
          ? []
          : [deployerPrivateKey]
    }
  },
  etherscan: {
    apiKey: {
      kairos: process.env.KAIASCAN_API_KEY ?? ""
    },
    customChains: [
      {
        network: "kairos",
        chainId: 1001,
        urls: {
          apiURL: "https://kairos-api.kaiascan.io/hardhat-verify",
          browserURL: "https://kairos.kaiascan.io"
        }
      }
    ]
  }
};

export default config;
