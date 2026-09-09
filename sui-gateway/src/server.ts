import { createHash, timingSafeEqual } from 'node:crypto';
import { createServer, type IncomingMessage, type ServerResponse } from 'node:http';
import { pathToFileURL } from 'node:url';
import { Ed25519Keypair } from '@mysten/sui/keypairs/ed25519';
import { GatewayError, requireValue } from './contracts.js';
import { readConfig } from './config.js';
import { Gateway } from './gateway.js';
import { GrpcChain } from './sdk.js';
import { DurableJournal } from './journal.js';

const sha = (value: string) => createHash('sha256').update(value).digest();
export function authorized(header: string | undefined, token: string): boolean {
  // Hash to a fixed length first; secret length never controls the comparison.
  const supplied = typeof header === 'string' && header.startsWith('Bearer ') ? header.slice(7) : '';
  return timingSafeEqual(sha(supplied), sha(token));
}
async function body(req: IncomingMessage): Promise<unknown> {
  requireValue(req.headers['content-type']?.split(';')[0]?.trim().toLowerCase() === 'application/json',
    'INVALID_INPUT', 'Content-Type must be application/json');
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > 512_000) throw new GatewayError('INVALID_INPUT', 'Request body exceeds 512000 bytes', false, 413);
    chunks.push(Buffer.from(chunk));
  }
  try { return JSON.parse(Buffer.concat(chunks).toString('utf8')); }
  catch { throw new GatewayError('INVALID_INPUT', 'Malformed JSON body'); }
}
function send(response: ServerResponse, status: number, value: unknown) {
  response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff' });
  response.end(JSON.stringify(value));
}
export function createGatewayServer(gateway: Gateway, token: string) {
  requireValue(token.length >= 32 && !/\s/.test(token), 'CONFIG_INVALID', 'A strong gateway bearer token is required');
  const routes: Record<string, (input: unknown) => Promise<unknown>> = {
    '/v1/issuer-key': input => gateway.issuerKey(input), '/v1/batch': input => gateway.batch(input),
    '/v1/status': input => gateway.status(input), '/v1/prepare-batch': input => gateway.prepareBatch(input),
    '/v1/prepare-status': input => gateway.prepareStatus(input), '/v1/broadcast': input => gateway.broadcast(input),
    '/v1/receipt': input => gateway.receipt(input),
  };
  const server = createServer({ maxHeaderSize: 8192, requestTimeout: 20_000, headersTimeout: 10_000 }, async (req, res) => {
    try {
      if (!authorized(req.headers.authorization, token)) throw new GatewayError('UNAUTHORIZED', 'Bearer authentication required', false, 401);
      // This is a server-to-server sidecar, never a browser-facing API.
      if (req.headers.origin) throw new GatewayError('FORBIDDEN', 'Browser-origin requests are not accepted', false, 403);
      if (req.method === 'GET' && req.url === '/v1/identity') {
        send(res, 200, { ok: true, data: await gateway.initialize() });
      } else if (req.method === 'POST' && req.url && routes[req.url]) {
        send(res, 200, { ok: true, data: await routes[req.url]!(await body(req)) });
      } else throw new GatewayError('NOT_FOUND', 'Unknown gateway endpoint', false, 404);
    } catch (error) {
      // Do not echo RPC exception text, request bodies, stack traces, URLs, or keys.
      const safe = error instanceof GatewayError ? error : new GatewayError('BLOCKCHAIN_RPC_UNAVAILABLE',
        'Sui RPC operation failed; no success or absence may be inferred', true, 502);
      send(res, safe.status, { ok: false, error: { code: safe.code, message: safe.message, retryable: safe.retryable } });
    }
  });
  server.keepAliveTimeout = 5000;
  return server;
}

export async function startGateway(env: NodeJS.ProcessEnv = process.env) {
  const config = readConfig(env);
  const signer = Ed25519Keypair.fromSecretKey(config.relayerPrivateKey);
  const gateway = new Gateway(config.identity, new GrpcChain(config), signer, new DurableJournal(config.journalDirectory), config.gasBudget);
  await gateway.initialize(); // Fail closed before listening if identity cannot be proven.
  const server = createGatewayServer(gateway, config.token);
  server.listen(config.port, config.bind, () => process.stdout.write('Trekkey Sui gateway ready on configured loopback endpoint.\n'));
  for (const signal of ['SIGTERM', 'SIGINT'] as const) process.on(signal, () => server.close());
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  startGateway().catch(() => { process.stderr.write('Sui gateway startup failed; verify deployment identity, RPC availability, and secret configuration.\n'); process.exitCode = 1; });
}
