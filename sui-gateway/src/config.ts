import { hex, requireValue, uint, type Identity } from './contracts.js';
import { isAbsolute } from 'node:path';

export type Config = {
  identity: Identity; bind: '127.0.0.1' | '::1'; port: number; token: string; rpcUrl: string;
  relayerPrivateKey: string; gasBudget: string; timeoutMs: number; journalDirectory: string;
};
export function readConfig(env: NodeJS.ProcessEnv): Config {
  const network = env.SUI_NETWORK ?? 'testnet';
  requireValue(network === 'testnet' || network === 'localnet' || network === 'mainnet', 'CONFIG_INVALID', 'Invalid SUI_NETWORK');
  const bind = env.SUI_GATEWAY_BIND ?? '127.0.0.1';
  requireValue(bind === '127.0.0.1' || bind === '::1', 'CONFIG_INVALID', 'Gateway must bind to a loopback address');
  const token = env.SUI_GATEWAY_TOKEN;
  requireValue(typeof token === 'string' && token.length >= 32 && !/\s/.test(token), 'CONFIG_INVALID', 'SUI_GATEWAY_TOKEN must contain at least 32 non-whitespace characters');
  const chainIdentifier = (env.SUI_CHAIN_IDENTIFIER ?? '').replace(/^0x/, '').toLowerCase();
  requireValue(/^[0-9a-f]{8}$/.test(chainIdentifier), 'CONFIG_INVALID', 'SUI_CHAIN_IDENTIFIER must contain exactly four bytes');
  const rpcUrl = env.SUI_GRPC_URL ?? (network === 'localnet' ? 'http://127.0.0.1:9000' : `https://fullnode.${network}.sui.io:443`);
  const url = new URL(rpcUrl);
  requireValue(network !== 'localnet' || ['127.0.0.1', '[::1]', 'localhost'].includes(url.hostname),
    'CONFIG_INVALID', 'Localnet RPC must be loopback; a public network cannot be relabeled localnet');
  requireValue(!url.username && !url.password && !url.hash && !url.search &&
    (url.protocol === 'https:' || (network === 'localnet' && url.protocol === 'http:' && ['127.0.0.1', '[::1]', 'localhost'].includes(url.hostname))),
  'CONFIG_INVALID', 'Use HTTPS RPC, or loopback HTTP only for localnet; URLs must not contain credentials');
  const port = Number(uint(env.SUI_GATEWAY_PORT ?? '9187', 16, 'port'));
  requireValue(port > 0, 'CONFIG_INVALID', 'Gateway port must be positive');
  const gasBudget = uint(env.SUI_GAS_BUDGET ?? '50000000');
  requireValue(BigInt(gasBudget) > 0n && BigInt(gasBudget) <= 1_000_000_000n, 'CONFIG_INVALID', 'Gas budget must be 1..1000000000 MIST');
  const timeoutMs = Number(uint(env.SUI_GATEWAY_RPC_TIMEOUT_MS ?? '15000', 32));
  requireValue(timeoutMs >= 1000 && timeoutMs <= 60000, 'CONFIG_INVALID', 'RPC timeout must be 1000..60000 ms');
  const relayerPrivateKey = env.SUI_RELAYER_PRIVATE_KEY ?? '';
  requireValue(relayerPrivateKey.startsWith('suiprivkey'), 'CONFIG_INVALID', 'An explicitly configured Ed25519 SUI_RELAYER_PRIVATE_KEY is required');
  const journalDirectory = env.SUI_GATEWAY_JOURNAL_DIR ?? '';
  requireValue(isAbsolute(journalDirectory) && journalDirectory !== '/', 'CONFIG_INVALID', 'SUI_GATEWAY_JOURNAL_DIR must identify a dedicated persistent absolute directory');
  return { identity: { network, chainIdentifier, packageId: hex(env.SUI_PACKAGE_ID, 32, 'packageId', true),
    registryId: hex(env.SUI_REGISTRY_ID, 32, 'registryId', true), protocolVersion: 1 }, bind, port, token,
    rpcUrl, relayerPrivateKey, gasBudget, timeoutMs, journalDirectory };
}
