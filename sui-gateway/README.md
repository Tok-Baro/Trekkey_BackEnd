# Trekkey Sui SDK gateway

Backend-only TypeScript bridge for the Java `BlockchainAnchorPort`. Uses pinned `@mysten/sui@2.29.0` and `SuiGrpcClient`; no deprecated JSON-RPC client and no frontend changes. Node 22+ is required.

## Run and trust boundary

```sh
npm ci --ignore-scripts
npm run build
npm test
# Supply the variables described in .env.example through your secret manager/process supervisor.
npm start
```

The process does not load `.env` files automatically. `SUI_GATEWAY_TOKEN` must be at least 32 non-whitespace characters. `SUI_RELAYER_PRIVATE_KEY` is an explicitly provided **Ed25519** `suiprivkey...`, never an issuer's key. HTTP binds only to `127.0.0.1` (default port 9187) or `::1`; every endpoint requires `Authorization: Bearer …`. Browser-origin requests are rejected. Do not expose this service through a public proxy.

Before listening, and at read/prepare/broadcast/receipt boundaries, the gateway verifies the RPC chain name/genesis identifier and the shared Registry's exact type, object ID, original package ID and chain bytes. SDK v2 returns a full base58 genesis digest; the approval's `chainIdentifier` is its first four bytes, encoded as eight lowercase hex characters. Mainnet prepares/broadcasts are always disabled. A localnet RPC must be loopback. The configured RPC is a trusted source of ledger data: this gateway is **not** a cryptographic light client and does not independently verify validator quorum signatures.

This version targets the original `credential_registry` package ABI in `../contracts-sui`. Package upgrades require a separate ABI/identity review; do not silently point the manifest at a different package. Timestamps are Unix **seconds**, gas budget is MIST, and all u64 JSON values are decimal strings. No actual deployments, wallet imports, faucets or chain writes occur from installing/building/testing the default unit suite.

## API contract

Success is `{ "ok": true, "data": {...} }`; failure is `{ "ok": false, "error": { "code": "UPPER_SNAKE_CASE", "message": "safe explanation", "retryable": false } }`. Unknown/malformed RPC results are never interpreted as absence or successful anchoring.

| Endpoint | Request / response |
| --- | --- |
| `GET /v1/identity` | `network`, `chainIdentifier`, `packageId`, `registryId`, `protocolVersion: 1` |
| `POST /v1/issuer-key` | `{issuerId,keyVersion}` → Java `OnChainIssuerKey` fields; `signer` is a **20-byte** secp256k1 issuer address |
| `POST /v1/batch` | `{batchIdHash}` → Java `OnChainBatch` fields, safe `exists:false` zeros for actual not-found only |
| `POST /v1/status` | `{issuerId,credentialIdHash}` → `state:NONE/REVOKED/SUPERSEDED`, timestamps, key version, replacement hash |
| `POST /v1/prepare-batch` | `{approval:BatchApproval,issuerSignature:"0x…65bytes"}` → prepared record below |
| `POST /v1/prepare-status` | Same, using `StatusApproval`; action `"0"`=REVOKE / `"1"`=SUPERSEDE |
| `POST /v1/broadcast` | `{transactionHash,signedRawTransaction}` → matching `transactionHash`, native `transactionDigest`, `accepted:true` |
| `POST /v1/receipt` | `{transactionHash,operationType:ANCHOR_BATCH/REVOKE/SUPERSEDE}` → state and checkpoint evidence |

Prepared records contain `transactionHash` (0x-prefixed decoded Sui digest bytes), native base58 `transactionDigest`, `transactionNonce:null`, a **32-byte** Sui `relayerAddress`, and `signedRawTransaction` (base64 of a versioned JSON envelope containing exact BCS bytes and a serialized Sui signature). Preparation builds/simulates and signs but **never executes**. Java must persist this exact result before `/broadcast`. Broadcast checks digest, Ed25519 signature, configured sender/gas owner/budget, and exactly one allowed package/module/function/Registry/Clock Move call; it does not rebuild or re-sign. SDK-resolved pure arguments must match the original issuer approval before signing.

Receipt `PENDING` means no transaction or no checkpoint yet; `REVERTED` requires checkpointed execution failure. `CONFIRMED` requires successful effects, a real checkpoint sequence/digest and exactly one matching operation event from the configured package/module/Registry. Legacy `blockNumber` and `blockHash` are **checkpoint aliases**, not EVM block data. Native `transactionDigest` and `checkpointDigest` are also returned. A broadcast timeout is ambiguous: reconcile the same digest and retry only the exact stored envelope.

## Durable prepare/gas journal (required)

Set `SUI_GATEWAY_JOURNAL_DIR` to a dedicated, persistent, owner-only (0700) directory. Journal files are 0600; keys are never stored there, but already signed transactions are sensitive operational data. Use a local filesystem with atomic exclusive create, rename and working `fsync`, or a shared filesystem whose equivalent guarantees have been independently verified. **All instances using the same relayer must share the same journal.** Independent volumes, multiple unrelated signers using the key, snapshots restored without reconciliation, or a disposable container filesystem defeat the reservation guarantee.

An approval's Sui-domain digest is its idempotency key. The journal records a durable exclusive `BUILDING` claim, then exact `UNSIGNED` BCS bytes and gas object/version references, reserves every gas reference using exclusive files, and only then signs. `SIGNED` envelopes are atomically renamed and fsynced before responding. A process mutex serializes local prepares; file claims/reservations coordinate separate instances. A different approval cannot be signed against an already reserved gas version. The default gas-coin path is supported; address-balance gas mode is deliberately rejected until its different replay rules have a journal implementation.

| Persisted state | Recovery behavior |
| --- | --- |
| `SIGNED` | Return the identical previously persisted result, even after restart/response loss. Never rebuild. |
| `UNSIGNED` | Validate saved bytes, complete/check the same gas reservations, and sign those exact bytes. No gas refresh. |
| `BUILDING`, malformed file, conflicting reservation | Stop with an explicit recovery/conflict error. No automatic deletion, new transaction or TTL expiry. |

There is intentionally no automatic journal cleanup. Preserve journal and Java outbox together in backups. Reconcile every signed/pending transaction and on-chain gas object version before any operator-approved recovery, relayer rotation or cleanup. An expired approval or stale reserved coin is not permission to discard an uncertain signed transaction. A newly consumed coin version can be reserved for a new approval while old reservations remain as evidence.

Temporal preflight uses the canonical shared Sui Clock (`0x6`), not the gateway host's wall clock. If a status approval's `effectiveAt` is ahead of chain time, the gateway returns retryable `BLOCKCHAIN_CHAIN_CLOCK_BEHIND` **before creating a journal claim**; the same request can be retried after the chain catches up. Other failures after the `BUILDING` claim but before exact bytes are persisted remain deliberately fail-closed and require manual reconciliation. No operator recovery should infer absence of a signature merely from an HTTP error.

## Offline school approval signing

The school signature remains canonical low-s secp256k1 `r||s||v` (65 bytes, v27/28; v0/1 input is normalized). It is separate from the relayer's serialized Ed25519 Sui signature. Digest:

```text
domain = keccak256(UTF8("TREKKEY_SUI_APPROVAL_V1") || chainIdentifier4 || packageId32 || registryId32)
digest = keccak256(0x1901 || domain || existing Eip712.java structHash)
```

This is not an ordinary EIP-712 wallet domain. Copy the backend's approval JSON (`scheme`, `signatureScheme`, `domain`, `primaryType`, `message`, `digestHex`). Supply a separately trusted manifest with `network`, `chainIdentifier`, `packageId`, `registryId`, `protocolVersion:1`, and the independently expected school's 20-byte signer address.

```sh
# FD 3 must already be opened privately by the operator to a 0x-prefixed 32-byte issuer key.
# Do not pass a secret key as a command argument or save it in this repository.
npm run sign-approval -- --manifest deployment.json --input approval.json --expected-signer 0x... --key-fd 3

# Read-only manifest/RPC/Registry inspection; needs no signing key or gateway token.
npm run inspect -- --manifest deployment.json --rpc-url https://fullnode.testnet.sui.io:443
```

The signing command recomputes the digest, checks the manifest and signer, prints only approval/signature metadata, and never submits a transaction. The shared `../contracts-sui/test-fixtures/approval-v1.json` synthetic vectors are checked byte-for-byte against the Node implementation and are also used by Move/Java tests.

## Verification and limits

### Explicit synthetic testnet deployment CLI

`node dist/src/deploy-testnet.js` is a separate operator-only CLI; importing it or running unit tests never initializes keys or deploys. It only accepts the official `https://fullnode.testnet.sui.io` endpoint and requires the independently verified **full base58 genesis digest**, plus the SHA256 of a reviewed production `sui move build --dump-bytecode-as-base64 --no-tree-shaking` JSON file. Inspect that build against `contracts-sui` source before use; a supplied file hash is an operator trust anchor, not a bytecode security audit.

```sh
node dist/src/deploy-testnet.js --init-keys --state-dir /srv/trekkey-sui-testnet/secrets/deployment
node dist/src/deploy-testnet.js --deploy --state-dir /srv/trekkey-sui-testnet/secrets/deployment --expected-genesis VERIFIED_FULL_BASE58_GENESIS --bytecode /absolute/reviewed-bytecode.json --bytecode-sha256 VERIFIED_RAW_FILE_SHA256
```

These commands are **not** part of installation or testing and require separate operator authorization. The state directory must be canonical, owned by the executing UID, and 0700. `--init-keys` creates EXCL 0600 `deployer.key`, `relayer.key` (separate Ed25519 `suiprivkey` values), `synthetic-issuer.key` (separate secp256k1 `0x` scalar), and public identity metadata. It never overwrites existing files or prints private keys. The generated organization UUID is synthetic and its issuer hash matches Java `Hashing.issuerId`; this CLI cannot register a supplied real institution. Fund testnet addresses only through a separately reviewed testnet faucet step; this CLI does not contact a faucet or transfer funds between accounts.

The only deployment phases are publish, create Registry, and register the synthetic issuer/relayer. Each has a fixed 0.1 SUI gas budget; their total budget is 0.3 SUI, below the 1 SUI ceiling. Before any submission, the exact transaction bytes, signature, digest, and immutable intent are stored and fsynced. Retries query the same digest or retransmit only the same stored bytes; no completed phase rebuilds or creates another registry/package. RPC-resolved commands, arguments, object identities, sender, and gas budget are checked before signing. Full RPC testnet/genesis identity is rechecked before each submission boundary.

`deployment.json` contains public network/chain/package/registry IDs, cap IDs, deployer/relayer addresses, synthetic organization UUID/issuer ID/signer, artifact hash and transaction digests. Final actual BCS reads verify registry identity, issuer, relayer authorization, and AdminCap/UpgradeCap binding/custody. Preserve the entire private state directory. Interrupted partial files, altered artifacts/configuration, or an exclusive `deployment.lock` stop the CLI; do not delete state or automatically regenerate keys/transactions. A crash-held lock may be removed only by an operator after establishing that no deployer is running and reconciling the saved transactions. A failed receipt requires explicit recovery, never a fresh state directory used as an automatic retry.

`npm test` uses real SDK crypto/BCS code, an injected SDK test transport/fake chain, a temporary durable journal and an ephemeral loopback HTTP listener. It tests approval parity, unsafe integer rejection, prepared-byte integrity, network/signer boundaries, exact event/checkpoint evidence, authentication/error redaction, same-approval restart idempotency, gas-reservation contention and crash recovery. Local bind permission is required for the HTTP test. Public testnet/mainnet E2E, production deployment identity, wallet custody, production filesystem durability and disaster-recovery operations require separate verification. The optional `test/localnet-smoke.mjs` is an explicit localnet-only integration script, not part of the default test command.
