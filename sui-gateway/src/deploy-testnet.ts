/** Explicit operator CLI. Importing this module never creates keys or submits a transaction. */
import { createHash, randomBytes, randomUUID } from 'node:crypto';
import { constants } from 'node:fs';
import { lstat, mkdir, open, readFile, realpath, unlink } from 'node:fs/promises';
import { isAbsolute, join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { bcs } from '@mysten/sui/bcs';
import { TransactionError, type SuiClientTypes } from '@mysten/sui/client';
import { Ed25519Keypair } from '@mysten/sui/keypairs/ed25519';
import { SuiGrpcClient } from '@mysten/sui/grpc';
import { Transaction, TransactionDataBuilder } from '@mysten/sui/transactions';
import { fromBase58, normalizeStructTag } from '@mysten/sui/utils';
import { verifyTransactionSignature } from '@mysten/sui/verify';
import { secp256k1 } from '@noble/curves/secp256k1.js';
import { keccak_256 } from '@noble/hashes/sha3.js';
import { GrpcChain } from './sdk.js';
import * as C from './codecs.js';
import { bytes, hexBytes, requireValue } from './contracts.js';

export const GAS_BUDGET = 100_000_000n;
export const MAX_TOTAL_GAS_BUDGET = GAS_BUDGET * 3n; // Three phases; at most 0.3 SUI of gas budgets.
export const UpgradeCapBcs = bcs.struct('UpgradeCap', { id: bcs.Address, package: bcs.Address, version: bcs.u64(), policy: bcs.u8() });

/** Actual Rust UpgradeCap::new initializes version=1 (not the stale Move field comment's zero).
 * Pinned framework ae59d771... crates/sui-types/src/move_package.rs:634-639. */
export function validateInitialUpgradeCap(content: Uint8Array, capId: string, packageId: string) {
  const cap = C.parseBcs(UpgradeCapBcs, content);
  ensure(cap.id === capId && cap.package === packageId && cap.version === '1' && cap.policy === 0,
    'Initial UpgradeCap binding/version/policy readback failed');
  return cap;
}
const sha = (value: string | Uint8Array) => createHash('sha256').update(value).digest('hex');
const missing = (error: unknown) => (error as NodeJS.ErrnoException)?.code === 'ENOENT';
const ensure = (condition: unknown, message: string) => requireValue(condition, 'DEPLOYMENT_REFUSED', message);

export class PrivateState {
  constructor(readonly directory: string) {}
  async initialize() {
    ensure(isAbsolute(this.directory) && resolve(this.directory) === this.directory && this.directory !== '/', 'A dedicated absolute state directory is required');
    await mkdir(this.directory, { recursive: true, mode: 0o700 });
    const stat = await lstat(this.directory);
    ensure(stat.isDirectory() && !stat.isSymbolicLink() && (stat.mode & 0o777) === 0o700 &&
      stat.uid === process.getuid?.() && await realpath(this.directory) === this.directory, 'State directory must be canonical, owner-only 0700');
  }
  private path(name: string) { ensure(/^[a-z0-9.-]+$/.test(name), 'Invalid state filename'); return join(this.directory, name); }
  async sync() { const dir = await open(this.directory, constants.O_RDONLY); try { await dir.sync(); } finally { await dir.close(); } }
  async read(name: string): Promise<string | null> {
    let file;
    try { file = await open(this.path(name), constants.O_RDONLY | constants.O_NOFOLLOW); }
    catch (error) { if (missing(error)) return null; throw error; }
    try {
      const stat = await file.stat();
      ensure(stat.isFile() && stat.uid === process.getuid?.() && (stat.mode & 0o777) === 0o600 && stat.size <= 8_000_000, 'State file must be owner-only 0600 and bounded');
      return await file.readFile('utf8');
    } finally { await file.close(); }
  }
  async create(name: string, content: string) {
    const file = await open(this.path(name), constants.O_CREAT | constants.O_EXCL | constants.O_WRONLY | constants.O_NOFOLLOW, 0o600);
    try { await file.writeFile(content); await file.sync(); } finally { await file.close(); }
    await this.sync(); // Partial files are never replaced or silently regenerated.
  }
  async lock() { await this.create('deployment.lock', 'exclusive operator deployment\n'); }
  async unlock() { await unlink(this.path('deployment.lock')); await this.sync(); }
}

export function syntheticIssuerId(uuid: string): string {
  ensure(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(uuid), 'Synthetic organization must have a UUID v4');
  return hexBytes(keccak_256(Buffer.concat([
    keccak_256(new TextEncoder().encode('TREKKEY_ISSUER_ID_V1')),
    keccak_256(new TextEncoder().encode(uuid)),
  ])));
}
type PublicKeys = { synthetic: true; organizationPublicId: string; issuerUuid: string; issuerId: string;
  deployerAddress: string; relayerAddress: string; issuerSigner: string; signerAddress: string; keyVersion: 1 };
export async function initKeys(state: PrivateState): Promise<PublicKeys> {
  await state.initialize();
  await state.create('keys-init.claim', 'Synthetic testnet keys only. Never overwrite or regenerate this state.\n');
  const deployer = Ed25519Keypair.generate(), relayer = Ed25519Keypair.generate();
  const secret = randomBytes(32), issuerUuid = randomUUID();
  try {
    const signerAddress = hexBytes(keccak_256(secp256k1.getPublicKey(secret, false).slice(1)).slice(12));
    const result: PublicKeys = { synthetic: true, organizationPublicId: issuerUuid, issuerUuid, issuerId: syntheticIssuerId(issuerUuid),
      deployerAddress: deployer.toSuiAddress(), relayerAddress: relayer.toSuiAddress(), issuerSigner: signerAddress, signerAddress, keyVersion: 1 };
    await state.create('deployer.key', `${deployer.getSecretKey()}\n`);
    await state.create('relayer.key', `${relayer.getSecretKey()}\n`);
    await state.create('synthetic-issuer.key', `${hexBytes(secret)}\n`);
    await state.create('keys-public.json', JSON.stringify(result, null, 2));
    return result;
  } finally { secret.fill(0); }
}

export function validateEndpoint(rpcUrl: string, expectedGenesis: string) {
  const url = new URL(rpcUrl);
  ensure(url.origin === 'https://fullnode.testnet.sui.io' && (url.pathname === '/' || url.pathname === '') &&
    !url.username && !url.password && !url.search && !url.hash, 'Only the official HTTPS testnet fullnode is allowed');
  ensure(fromBase58(expectedGenesis).length === 32, 'An explicit complete expected testnet genesis digest is required');
}
export async function verifyTestnet(client: SuiGrpcClient, expectedGenesis: string) {
  const { response } = await client.ledgerService.getServiceInfo({}, { abort: AbortSignal.timeout(15000) });
  ensure(response.chain === 'testnet' && response.chainId === expectedGenesis, 'RPC is not the exact expected testnet genesis');
  return hexBytes(fromBase58(expectedGenesis).slice(0, 4)).slice(2);
}

type Bytecode = { modules: string[]; dependencies: string[]; digest: number[] };
export function parseBytecode(contents: string, expectedSha256: string): Bytecode {
  ensure(contents.length <= 6_000_000 && /^[0-9a-f]{64}$/.test(expectedSha256) && sha(contents) === expectedSha256, 'Reviewed bytecode file SHA256 mismatch');
  const value = JSON.parse(contents) as Bytecode;
  ensure(Array.isArray(value.modules) && value.modules.length === 2 && value.modules.every(m => typeof m === 'string' &&
    Buffer.from(m, 'base64').length > 0 && Buffer.from(m, 'base64').toString('base64') === m), 'Expected the two production Trekkey modules');
  ensure(Array.isArray(value.dependencies) && value.dependencies.length > 0 && value.dependencies.length <= 10 &&
    value.dependencies.every(id => /^0x[0-9a-f]{1,64}$/.test(id)), 'Malformed bytecode dependencies');
  ensure(Array.isArray(value.digest) && value.digest.length === 32 && value.digest.every(n => Number.isInteger(n) && n >= 0 && n <= 255), 'Malformed bytecode digest');
  return value;
}

// Remove SDK-only union/input hints; compare every command, pure input, and object identity after RPC resolution.
function clean(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(clean);
  if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).filter(([k, v]) =>
    v !== undefined && k !== '$kind' && !(k === 'type' && 'Input' in value)).map(([k, v]) => [k, clean(v)]));
  return value;
}
function semantic(tx: Transaction) {
  const data = tx.getData();
  return { commands: clean(data.commands), inputs: data.inputs.map(input => input.Pure ? { pure: input.Pure.bytes } : {
    objectId: input.UnresolvedObject?.objectId ?? input.Object?.SharedObject?.objectId ?? input.Object?.ImmOrOwnedObject?.objectId,
  }) };
}
type SavedPhase = { version: 1; intentHash: string; transactionBytes: string; signature: string; digest: string };
type ChainTx = SuiClientTypes.TransactionResult<{ effects: true; objectTypes: true }>;
export interface PhaseTransport {
  lookup(digest: string): Promise<ChainTx | null>;
  execute(data: Uint8Array, signature: string): Promise<void>;
  assertNetwork(): Promise<void>;
}
export async function savedPhase(state: PrivateState, name: string, intent: unknown, transaction: Transaction,
  signer: Ed25519Keypair, transport: PhaseTransport, build: () => Promise<Uint8Array>): Promise<ChainTx> {
  ensure(['publish', 'registry', 'authorize'].includes(name), 'Unknown deployment phase');
  transaction.setSender(signer.toSuiAddress()); transaction.setGasBudget(GAS_BUDGET);
  const expected = semantic(transaction), intentHash = sha(JSON.stringify({ intent, expected }));
  let saved: SavedPhase;
  const stored = await state.read(`phase-${name}.json`);
  if (stored === null) {
    const data = await build();
    const resolved = Transaction.from(data).getData();
    ensure(JSON.stringify(semantic(Transaction.from(data))) === JSON.stringify(expected), 'RPC changed the intended deployment transaction');
    ensure(resolved.sender === signer.toSuiAddress() && resolved.gasData.owner === signer.toSuiAddress() &&
      resolved.gasData.budget === GAS_BUDGET.toString(), 'Deployment gas/sender bound exceeded before signing');
    const signed = await signer.signTransaction(data);
    saved = { version: 1, intentHash, transactionBytes: Buffer.from(data).toString('base64'),
      signature: signed.signature, digest: TransactionDataBuilder.getDigestFromBytes(data) };
    await state.create(`phase-${name}.json`, JSON.stringify(saved)); // Sole signing persistence boundary, BEFORE any execute.
  } else saved = JSON.parse(stored) as SavedPhase;
  const data = Uint8Array.from(Buffer.from(saved.transactionBytes, 'base64'));
  ensure(saved.version === 1 && saved.intentHash === intentHash && Buffer.from(data).toString('base64') === saved.transactionBytes &&
    TransactionDataBuilder.getDigestFromBytes(data) === saved.digest && JSON.stringify(semantic(Transaction.from(data))) === JSON.stringify(expected),
  'Persisted deployment transaction does not match the immutable intent');
  const tx = Transaction.from(data).getData();
  ensure(tx.sender === signer.toSuiAddress() && tx.gasData.owner === signer.toSuiAddress() && tx.gasData.budget === GAS_BUDGET.toString(), 'Deployment gas/sender bound exceeded');
  await verifyTransactionSignature(data, saved.signature, { address: signer.toSuiAddress() });
  await transport.assertNetwork();
  let response = await transport.lookup(saved.digest);
  if (response === null) { await transport.execute(data, saved.signature); response = await transport.lookup(saved.digest); }
  ensure(response !== null, 'Submission is ambiguous; rerun with the exact existing state, never create a fresh deployment');
  const executed = response!.Transaction ?? response!.FailedTransaction;
  ensure(executed.digest === saved.digest && executed.status.success, 'Deployment transaction failed; preserve state for operator reconciliation');
  return response!;
}

async function loadKeys(state: PrivateState): Promise<{ publicKeys: PublicKeys; deployer: Ed25519Keypair }> {
  const publicKeys = JSON.parse((await state.read('keys-public.json')) ?? 'null') as PublicKeys;
  const deployer = Ed25519Keypair.fromSecretKey((await state.read('deployer.key'))?.trim() ?? '');
  const relayer = Ed25519Keypair.fromSecretKey((await state.read('relayer.key'))?.trim() ?? '');
  const issuerSecret = bytes((await state.read('synthetic-issuer.key'))?.trim() ?? '');
  try {
    const issuerSigner = hexBytes(keccak_256(secp256k1.getPublicKey(issuerSecret, false).slice(1)).slice(12));
    ensure(publicKeys?.synthetic === true && publicKeys.keyVersion === 1 && publicKeys.issuerId === syntheticIssuerId(publicKeys.issuerUuid) &&
      publicKeys.organizationPublicId === publicKeys.issuerUuid && publicKeys.deployerAddress === deployer.toSuiAddress() &&
      publicKeys.relayerAddress === relayer.toSuiAddress() && publicKeys.deployerAddress !== publicKeys.relayerAddress &&
      publicKeys.issuerSigner === issuerSigner && publicKeys.signerAddress === issuerSigner, 'Synthetic key identity mismatch');
    return { publicKeys, deployer };
  } finally { issuerSecret.fill(0); }
}

export async function deploy(state: PrivateState, rpcUrl: string, expectedGenesis: string, bytecodePath: string, expectedSha256: string) {
  validateEndpoint(rpcUrl, expectedGenesis);
  ensure(isAbsolute(bytecodePath), 'Bytecode input must be an absolute reviewed file');
  const artifactText = await readFile(bytecodePath, 'utf8'), artifact = parseBytecode(artifactText, expectedSha256);
  await state.initialize();
  await state.lock(); // Crash leaves this lock intentionally; operator must establish no running deployer before removing it.
  try {
    const { publicKeys: keys, deployer } = await loadKeys(state);
    const client = new SuiGrpcClient({ network: 'testnet', baseUrl: rpcUrl, timeout: 15000 });
    const chainIdentifier = await verifyTestnet(client, expectedGenesis);
    const intent = { network: 'testnet', genesisDigest: expectedGenesis, chainIdentifier, bytecodeSha256: expectedSha256,
      keys, gasBudgetPerPhase: GAS_BUDGET.toString(), maximumTotalGasBudget: MAX_TOTAL_GAS_BUDGET.toString() };
    const existingIntent = await state.read('deployment-intent.json');
    if (existingIntent === null) await state.create('deployment-intent.json', JSON.stringify(intent));
    else ensure(existingIntent === JSON.stringify(intent), 'Deployment configuration differs from the existing immutable intent');
    const transport: PhaseTransport = {
      async assertNetwork() { await verifyTestnet(client, expectedGenesis); },
      async lookup(digest) {
        try { return await client.getTransaction({ digest, include: { effects: true, objectTypes: true } }); }
        catch (error) { if (error instanceof TransactionError && error.reason === 'notFound') return null; throw error; }
      },
      async execute(transaction, signature) {
        await client.executeTransaction({ transaction, signatures: [signature] });
        await client.waitForTransaction({ digest: TransactionDataBuilder.getDigestFromBytes(transaction), timeout: 45000 });
      },
    };
    const run = async (name: string, tx: Transaction) => {
      const response = await savedPhase(state, name, intent, tx, deployer, transport, () => tx.build({ client }));
      return response.Transaction ?? response.FailedTransaction;
    };
    const publish = new Transaction();
    const upgrade = publish.publish(artifact);
    publish.transferObjects([upgrade], keys.deployerAddress);
    const published = await run('publish', publish);
    const packageId = published.effects!.changedObjects.find(o => o.outputState === 'PackageWrite')?.objectId;
    const upgradeCapId = Object.entries(published.objectTypes!).find(([, type]) => type.endsWith('::package::UpgradeCap'))?.[0];
    ensure(packageId && upgradeCapId, 'Publish receipt lacks package/UpgradeCap evidence');
    const create = new Transaction();
    create.moveCall({ target: `${packageId}::credential_registry::create_registry`, arguments: [create.pure.vector('u8', bytes(chainIdentifier))] });
    const created = await run('registry', create);
    const findCreated = (type: string) => Object.entries(created.objectTypes!).find(([, t]) => normalizeStructTag(t) === `${packageId}::credential_registry::${type}`)?.[0];
    const registryId = findCreated('Registry'), adminCapId = findCreated('AdminCap');
    ensure(registryId && adminCapId, 'Registry receipt lacks Registry/AdminCap evidence');
    const authorize = new Transaction();
    authorize.moveCall({ target: `${packageId}::credential_registry::set_relayer`, arguments: [authorize.object(registryId!),
      authorize.object(adminCapId!), authorize.pure.address(keys.relayerAddress), authorize.pure.bool(true)] });
    authorize.moveCall({ target: `${packageId}::credential_registry::register_issuer_key`, arguments: [authorize.object(registryId!),
      authorize.object(adminCapId!), authorize.object('0x6'), authorize.pure.vector('u8', bytes(keys.issuerId)),
      authorize.pure.u64(1), authorize.pure.vector('u8', bytes(keys.issuerSigner))] });
    const authorized = await run('authorize', authorize);
    const identity = { network: 'testnet' as const, chainIdentifier, packageId: packageId!, registryId: registryId!, protocolVersion: 1 as const };
    const chain = new GrpcChain({ identity, rpcUrl, timeoutMs: 15000 });
    await verifyTestnet(client, expectedGenesis);
    const object = await chain.object(registryId!), registry = C.parseBcs(C.Registry, object.content);
    ensure(object.shared && normalizeStructTag(object.type) === `${packageId}::credential_registry::Registry` && registry.id === registryId &&
      registry.package_id === packageId && hexBytes(registry.chain_identifier).slice(2) === chainIdentifier && !registry.paused,
    'Final registry identity readback failed');
    const issuer = await chain.field(registry.issuer_keys.id, `${packageId}::credential_registry::IssuerKeyId`,
      C.IssuerKeyId.serialize({ issuer_id: bytes(keys.issuerId), key_version: '1' }).toBytes());
    ensure(issuer && normalizeStructTag(issuer.type) === `${packageId}::credential_registry::IssuerKey`, 'Final issuer type readback failed');
    const issuerValue = C.parseBcs(C.IssuerKey, issuer!.bcs);
    ensure(hexBytes(issuerValue.signer) === keys.issuerSigner && issuerValue.valid_until === '0' && issuerValue.compromised_at === '0', 'Final issuer readback failed');
    const relayer = await chain.field(registry.relayers.id, 'address', bcs.Address.serialize(keys.relayerAddress).toBytes());
    ensure(relayer?.type === 'bool' && C.parseBcs(bcs.bool(), relayer!.bcs), 'Final relayer readback failed');
    const { object: admin } = await client.getObject({ objectId: adminCapId!, include: { content: true } });
    const { object: upgradeObject } = await client.getObject({ objectId: upgradeCapId!, include: { content: true } });
    const adminValue = C.parseBcs(bcs.struct('AdminCap', { id: bcs.Address, registry_id: bcs.Address }), admin.content);
    validateInitialUpgradeCap(upgradeObject.content, upgradeCapId!, packageId!);
    ensure(normalizeStructTag(admin.type) === `${packageId}::credential_registry::AdminCap` && adminValue.id === adminCapId && adminValue.registry_id === registryId &&
      admin.owner.AddressOwner === keys.deployerAddress && normalizeStructTag(upgradeObject.type) === normalizeStructTag('0x2::package::UpgradeCap') &&
      upgradeObject.owner.AddressOwner === keys.deployerAddress, 'Final AdminCap/UpgradeCap custody readback failed');
    const manifest = { ...identity, ...keys, genesisDigest: expectedGenesis, adminCapId, upgradeCapId,
      bytecodeSha256: expectedSha256, gasBudgetPerPhase: GAS_BUDGET.toString(), maximumTotalGasBudget: MAX_TOTAL_GAS_BUDGET.toString(),
      transactions: { publish: published.digest, registry: created.digest, authorize: authorized.digest } };
    const existing = await state.read('deployment.json');
    if (existing === null) await state.create('deployment.json', JSON.stringify(manifest, null, 2));
    else ensure(JSON.stringify(JSON.parse(existing)) === JSON.stringify(manifest), 'Final manifest differs from persisted deployment');
    return manifest;
  } finally { await state.unlock(); }
}

async function main() {
  const args = process.argv.slice(2), mode = args.shift();
  ensure(mode === '--init-keys' || mode === '--deploy', 'Use --init-keys or --deploy explicitly');
  const options = new Map<string, string>();
  while (args.length) {
    const key = args.shift()!, value = args.shift();
    ensure(['--state-dir', '--rpc-url', '--expected-genesis', '--bytecode', '--bytecode-sha256'].includes(key) && value && !options.has(key), 'Invalid or duplicate CLI option');
    options.set(key, value!);
  }
  const state = new PrivateState(options.get('--state-dir') ?? '');
  const result = mode === '--init-keys' ? await initKeys(state) : await deploy(state,
    options.get('--rpc-url') ?? 'https://fullnode.testnet.sui.io', options.get('--expected-genesis') ?? '',
    options.get('--bytecode') ?? '', options.get('--bytecode-sha256') ?? '');
  process.stdout.write(`${JSON.stringify(result)}\n`); // Public addresses/manifest only; never exception bodies or keys.
}
export function safeDeploymentFailure(error: unknown) {
  const value = error as { code?: unknown; name?: unknown; stack?: unknown };
  const codes = ['DEPLOYMENT_REFUSED', 'EEXIST', 'EACCES', 'EPERM', 'ENOENT', 'ELOOP', 'ETIMEDOUT'];
  const names = ['Error', 'SyntaxError', 'TypeError', 'RangeError', 'RpcError', 'TransactionError', 'ObjectError'];
  const code = typeof value?.code === 'string' && codes.includes(value.code) ? value.code : 'DEPLOYMENT_FAILED';
  const name = typeof value?.name === 'string' && names.includes(value.name) ? value.name : 'Error';
  const frame = typeof value?.stack === 'string' ? value.stack.split('\n').slice(1)
    .filter(line => !/\bat ensure\b/.test(line)).map(line => line.match(/deploy-testnet\.(ts|js):(\d+):(\d+)/)).find(Boolean) : null;
  const source = frame ? `deploy-testnet.${frame[1]}:${frame[2]}:${frame[3]}` : 'deploy-testnet';
  return { ok: false, code, name, source }; // Never echo arbitrary error messages, paths, stacks, RPC bodies or keys.
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch(error => { process.stderr.write(`${JSON.stringify(safeDeploymentFailure(error))}\n`); process.exitCode = 1; });
}
