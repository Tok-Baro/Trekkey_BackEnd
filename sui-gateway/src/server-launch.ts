import { closeSync, constants, fstatSync, openSync, readFileSync } from 'node:fs';
import { isAbsolute } from 'node:path';
import { pathToFileURL } from 'node:url';
import { Ed25519Keypair } from '@mysten/sui/keypairs/ed25519';
import { parseManifest } from './cli.js';
import { hex, record, requireValue } from './contracts.js';
import { readConfig } from './config.js';
import { startGateway } from './server.js';

// Container mounts must be regular files, not links. Root or this uid may own a
// public manifest; secret files must belong exclusively to the executing uid.
export function readMountedFile(path: string, secret: boolean): string {
  requireValue(isAbsolute(path), 'CONFIG_INVALID', 'Mounted configuration paths must be absolute');
  const fd = openSync(path, constants.O_RDONLY | constants.O_NOFOLLOW);
  try {
    const stat = fstatSync(fd), uid = process.getuid?.();
    requireValue(stat.isFile() && stat.nlink === 1 && stat.size > 0 && stat.size <= 65536,
      'CONFIG_INVALID', 'Mounted configuration must be a small regular file with one link');
    requireValue(uid !== undefined && (secret ? stat.uid === uid : stat.uid === uid || stat.uid === 0),
      'CONFIG_INVALID', 'Mounted configuration owner is not trusted');
    requireValue(secret ? (stat.mode & 0o777) === 0o600 : (stat.mode & 0o022) === 0,
      'CONFIG_INVALID', 'Secret files require mode 0600; manifests must not be group/world writable');
    return readFileSync(fd, 'utf8').trim();
  } finally { closeSync(fd); }
}

export function launchEnvironment(env: NodeJS.ProcessEnv): NodeJS.ProcessEnv {
  const raw = record(JSON.parse(readMountedFile(env.SUI_MANIFEST_FILE ?? '/run/manifest/deployment.json', false)));
  const identity = parseManifest(raw);
  requireValue(identity.network === 'testnet', 'CONFIG_INVALID', 'Server launcher permits testnet only');
  const key = readMountedFile(env.SUI_RELAYER_KEY_FILE ?? '/run/keys/relayer.key', true);
  const token = readMountedFile(env.SUI_GATEWAY_TOKEN_FILE ?? '/run/keys/gateway-token', true);
  requireValue(/^[\x21-\x7e]{32,512}$/.test(token), 'CONFIG_INVALID', 'Gateway token must be 32..512 printable ASCII characters');
  const keypair = Ed25519Keypair.fromSecretKey(key);
  requireValue(keypair.toSuiAddress() === hex(raw.relayerAddress, 32, 'manifest relayerAddress', true),
    'CONFIG_INVALID', 'Relayer key does not match deployment manifest');
  const authoritative = { SUI_NETWORK: identity.network, SUI_CHAIN_IDENTIFIER: identity.chainIdentifier,
    SUI_PACKAGE_ID: identity.packageId, SUI_REGISTRY_ID: identity.registryId };
  for (const [name, value] of Object.entries(authoritative)) {
    requireValue(!env[name] || env[name] === value, 'CONFIG_INVALID', 'Environment conflicts with deployment manifest');
  }
  const output = { ...env, ...authoritative, SUI_GATEWAY_TOKEN: token, SUI_RELAYER_PRIVATE_KEY: key };
  readConfig(output); // Fail locally before any RPC or listener is created.
  return output;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  Promise.resolve().then(() => startGateway(launchEnvironment(process.env))).catch(() => {
    process.stderr.write('Sui testnet launch failed; inspect file ownership, trusted manifest, and RPC availability without printing secrets.\n');
    process.exitCode = 1;
  });
}
