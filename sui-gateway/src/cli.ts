import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { normalizeStructTag } from '@mysten/sui/utils';
import { approvalDigest, canonicalIssuerSignature, recoverIssuer, signIssuer } from './approval.js';
import { parseBcs, Registry } from './codecs.js';
import { bytes, hex, hexBytes, MODULE, parseBatch, parseStatus, record, requireValue, SCHEME, type Identity } from './contracts.js';
import { GrpcChain } from './sdk.js';

export function parseManifest(value: unknown): Identity {
  const v = record(value);
  requireValue(v.protocolVersion === 1, 'INVALID_INPUT', 'Manifest protocolVersion must be 1');
  requireValue(v.network === 'testnet' || v.network === 'localnet' || v.network === 'mainnet', 'INVALID_INPUT', 'Manifest network required');
  requireValue(typeof v.chainIdentifier === 'string' && /^[0-9a-f]{8}$/.test(v.chainIdentifier), 'INVALID_INPUT', 'Manifest chainIdentifier must be eight lowercase hex digits');
  return { network: v.network, chainIdentifier: v.chainIdentifier, packageId: hex(v.packageId, 32, 'packageId', true),
    registryId: hex(v.registryId, 32, 'registryId', true), protocolVersion: 1 };
}
export function signApprovalPayload(input: unknown, identity: Identity, secret: Uint8Array, expectedSigner: string) {
  const payload = record(input), domain = record(payload.domain);
  requireValue(payload.scheme === SCHEME && payload.signatureScheme === 'secp256k1-recoverable-low-s', 'INVALID_INPUT', 'Unsupported approval signature scheme');
  requireValue(domain.chainIdentifier === identity.chainIdentifier && domain.packageId === identity.packageId && domain.registryId === identity.registryId,
    'INVALID_INPUT', 'Approval domain does not match the independently trusted deployment manifest');
  requireValue(payload.primaryType === 'BatchApproval' || payload.primaryType === 'StatusApproval', 'INVALID_INPUT', 'Unsupported approval type');
  const approval = payload.primaryType === 'BatchApproval' ? parseBatch(payload.message) : parseStatus(payload.message);
  const digestHex = hexBytes(approvalDigest(identity, approval));
  requireValue(payload.digestHex === digestHex, 'INVALID_INPUT', 'Displayed digest does not match the actual approval fields');
  const signature = signIssuer(identity, approval, secret), signer = recoverIssuer(identity, approval, canonicalIssuerSignature(signature));
  requireValue(signer === hex(expectedSigner, 20, 'expectedSigner', true), 'INVALID_INPUT', 'Signing key does not match the independently expected issuer address');
  return { scheme: SCHEME, primaryType: payload.primaryType, domain: payload.domain, digestHex, signer, signature };
}
function arg(name: string): string {
  const index = process.argv.indexOf(name);
  requireValue(index >= 0 && typeof process.argv[index + 1] === 'string' && !process.argv[index + 1]!.startsWith('--'),
    'INVALID_INPUT', `Missing ${name}`);
  return process.argv[index + 1]!;
}
async function main() {
  const identity = parseManifest(JSON.parse(readFileSync(arg('--manifest'), 'utf8')));
  if (process.argv[2] === 'sign-approval') {
    // No key in argv, repository, gateway, or approval JSON. Operator supplies an already-open private FD.
    const fdText = arg('--key-fd');
    requireValue(/^(?:[3-9]|[1-9][0-9]+)$/.test(fdText) && Number.isSafeInteger(Number(fdText)), 'INVALID_INPUT', 'Use a private key file descriptor >= 3');
    const secret = bytes(hex(readFileSync(Number(fdText), 'utf8').trim(), 32, 'issuer signing key', true));
    try {
      const output = signApprovalPayload(JSON.parse(readFileSync(arg('--input'), 'utf8')), identity, secret, arg('--expected-signer'));
      process.stdout.write(`${JSON.stringify(output, null, 2)}\n`);
    } finally { secret.fill(0); }
  } else if (process.argv[2] === 'inspect') {
    const rpcUrl = arg('--rpc-url'), url = new URL(rpcUrl);
    requireValue(!url.username && !url.password && !url.search && !url.hash &&
      (url.protocol === 'https:' || (identity.network === 'localnet' && url.protocol === 'http:')) &&
      (identity.network !== 'localnet' || ['127.0.0.1', '[::1]', 'localhost'].includes(url.hostname)),
    'INVALID_INPUT', 'RPC must use HTTPS, or localnet loopback HTTP, without embedded credentials');
    const chain = new GrpcChain({ identity, rpcUrl, timeoutMs: 15000 });
    requireValue(await chain.chainIdentifier() === identity.chainIdentifier, 'IDENTITY_MISMATCH', 'RPC chain does not match manifest');
    await chain.verifyPackage(identity.packageId);
    const object = await chain.object(identity.registryId), registry = parseBcs(Registry, object.content);
    requireValue(object.shared && object.objectId === identity.registryId &&
      normalizeStructTag(object.type) === `${identity.packageId}::${MODULE}::Registry` && registry.id === identity.registryId &&
      registry.package_id === identity.packageId && hexBytes(registry.chain_identifier).slice(2) === identity.chainIdentifier,
    'IDENTITY_MISMATCH', 'Registry does not match manifest');
    process.stdout.write(`${JSON.stringify({ verified: true, identity, paused: registry.paused, readOnly: true }, null, 2)}\n`);
  } else throw new Error('Use sign-approval or inspect');
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch(() => { process.stderr.write('Command failed; verify explicit arguments, trusted manifest, approval digest, and signer identity. No transaction was submitted.\n'); process.exitCode = 1; });
}
