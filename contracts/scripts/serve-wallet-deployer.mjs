import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { dirname, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import dotenv from "dotenv";

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const contractsDirectory = resolve(scriptDirectory, "..");
const uiDirectory = resolve(contractsDirectory, "wallet-deployer");

const assetRoutes = new Map([
  ["/", { path: resolve(uiDirectory, "index.html"), type: "text/html; charset=utf-8" }],
  ["/index.html", { path: resolve(uiDirectory, "index.html"), type: "text/html; charset=utf-8" }],
  ["/app.js", { path: resolve(uiDirectory, "app.js"), type: "text/javascript; charset=utf-8" }],
  [
    "/core.mjs",
    { path: resolve(uiDirectory, "core.mjs"), type: "text/javascript; charset=utf-8" }
  ],
  ["/styles.css", { path: resolve(uiDirectory, "styles.css"), type: "text/css; charset=utf-8" }],
  [
    "/ethers.js",
    {
      path: resolve(contractsDirectory, "node_modules/ethers/dist/ethers.min.js"),
      type: "text/javascript; charset=utf-8"
    }
  ],
  [
    "/artifact.json",
    {
      path: resolve(
        contractsDirectory,
        "artifacts/contracts/TrekkeyCredentialRegistryV1.sol/TrekkeyCredentialRegistryV1.json"
      ),
      type: "application/json; charset=utf-8"
    }
  ]
]);

const commonHeaders = {
  "Cache-Control": "no-store",
  "Content-Security-Policy":
    "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self' data:; base-uri 'none'; frame-ancestors 'none'; form-action 'none'",
  "Referrer-Policy": "no-referrer",
  "X-Content-Type-Options": "nosniff"
};

function value(env, name) {
  return env[name]?.trim() ?? "";
}

export function publicConfig(env) {
  return {
    expectedChainId: 1001,
    expectedDeployerAddress: value(env, "DEPLOYER_ADDRESS"),
    registryAddress: value(env, "REGISTRY_ADDRESS"),
    issuerPublicId: value(env, "ISSUER_PUBLIC_ID"),
    issuerKeyVersion: value(env, "ISSUER_KEY_VERSION") || "1",
    issuerSignerAddress: value(env, "ISSUER_SIGNER_ADDRESS"),
    relayerAddress: value(env, "RELAYER_ADDRESS"),
    explorerUrl: "https://kairos.kaiascan.io"
  };
}

export function assetFor(pathname) {
  return assetRoutes.get(pathname) ?? null;
}

export function isAllowedHost(host) {
  if (host === undefined) {
    return false;
  }
  return /^(127\.0\.0\.1|localhost)(:\d+)?$/.test(host);
}

export async function runtimeMetadata() {
  const debugPath = resolve(
    contractsDirectory,
    "artifacts/contracts/TrekkeyCredentialRegistryV1.sol/TrekkeyCredentialRegistryV1.dbg.json"
  );
  const debug = JSON.parse(await readFile(debugPath, "utf8"));
  const buildInfoDirectory = resolve(contractsDirectory, "artifacts/build-info");
  const buildInfoPath = resolve(dirname(debugPath), debug.buildInfo);
  const relativeBuildInfoPath = relative(buildInfoDirectory, buildInfoPath);
  if (
    relativeBuildInfoPath.startsWith("..") ||
    relativeBuildInfoPath.includes("/") ||
    relativeBuildInfoPath.includes("\\")
  ) {
    throw new Error("Hardhat build-info path is outside the expected directory.");
  }

  const buildInfo = JSON.parse(await readFile(buildInfoPath, "utf8"));
  const references =
    buildInfo.output.contracts["contracts/TrekkeyCredentialRegistryV1.sol"]
      .TrekkeyCredentialRegistryV1.evm.deployedBytecode.immutableReferences;
  const immutableReferences = Object.values(references)
    .flat()
    .map(({ start, length }) => ({ start, length }))
    .sort((left, right) => left.start - right.start);
  return { immutableReferences };
}

async function loadEnvironment() {
  let fileValues = {};
  try {
    fileValues = dotenv.parse(await readFile(resolve(contractsDirectory, ".env")));
  } catch (error) {
    if (error?.code !== "ENOENT") {
      throw error;
    }
  }
  return { ...fileValues, ...process.env };
}

function send(response, status, type, body) {
  response.writeHead(status, { ...commonHeaders, "Content-Type": type });
  response.end(body);
}

export function createWalletDeployerServer(env) {
  return createServer(async (request, response) => {
    try {
      if (!isAllowedHost(request.headers.host)) {
        send(response, 403, "text/plain; charset=utf-8", "Forbidden");
        return;
      }
      if (request.method !== "GET" && request.method !== "HEAD") {
        send(response, 405, "text/plain; charset=utf-8", "Method Not Allowed");
        return;
      }

      const url = new URL(request.url ?? "/", "http://127.0.0.1");
      if (url.pathname === "/config.json") {
        const body = JSON.stringify(publicConfig(env));
        send(
          response,
          200,
          "application/json; charset=utf-8",
          request.method === "HEAD" ? "" : body
        );
        return;
      }
      if (url.pathname === "/runtime-metadata.json") {
        const body = JSON.stringify(await runtimeMetadata());
        send(
          response,
          200,
          "application/json; charset=utf-8",
          request.method === "HEAD" ? "" : body
        );
        return;
      }

      const asset = assetFor(url.pathname);
      if (asset === null) {
        send(response, 404, "text/plain; charset=utf-8", "Not Found");
        return;
      }
      const body = await readFile(asset.path);
      send(response, 200, asset.type, request.method === "HEAD" ? "" : body);
    } catch (error) {
      console.error(error);
      send(response, 500, "text/plain; charset=utf-8", "Internal Server Error");
    }
  });
}

async function main() {
  const env = await loadEnvironment();
  const port = Number.parseInt(value(env, "WALLET_DEPLOYER_PORT") || "4173", 10);
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error("WALLET_DEPLOYER_PORT must be a valid TCP port.");
  }

  const server = createWalletDeployerServer(env);
  server.listen(port, "127.0.0.1", () => {
    console.log(`Kaia Wallet deployer: http://127.0.0.1:${port}`);
    console.log("Only public deployment configuration is exposed to the local page.");
  });
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  main().catch((error) => {
    console.error(error);
    process.exitCode = 1;
  });
}
