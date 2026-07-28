# Trekkey Credential Registry Contracts

`TrekkeyCredentialRegistryV1` anchors Merkle roots and individual correction states on Kaia. It never stores Credential JSON, PII, file metadata, or Merkle proofs on-chain.

## Fixed V1 rules

- OpenZeppelin Contracts is pinned to `5.4.0`, the latest 5.x release that compiles for Kaia's required London target. Contracts `5.5.x` and `5.6.x` use Cancun-only `mcopy` in the ECDSA import path.
- The EIP-712 domain is `TrekkeyCredentialRegistry`, version `1`, current `chainId`, and this contract address.
- A batch approval contains issuer ID, batch ID hash, root, schema hash, leaf count, tree version, issuer key version, approval nonce, and deadline.
- A status approval contains issuer ID, Credential ID hash, `REVOKE` or `SUPERSEDE`, replacement Credential ID hash, effective time, issuer key version, approval nonce, and deadline.
- A nonce is single-use per issuer across both approval types. The EIP-712 digest is also single-use.
- A status effective time must be non-zero and no later than the transaction timestamp. V1 records corrections immediately rather than scheduling future changes.
- Each status record stores its on-chain `recordedAt` timestamp so verifiers can apply later-retroactive key compromise information.
- `pause` blocks only `anchorBatch`. `revokeCredential` and `supersedeCredential` remain available.
- The Solidity compiler targets `evmVersion: "london"`, as required for Kairos and Kaia Mainnet compatibility.
- V1 has no batch revoke and no proxy upgrade path. Deploy a new contract version when logic changes.

## Local setup and tests

Use Node.js 22 LTS. The repository includes `.nvmrc`, and other Node.js versions are not part of the tested toolchain.

```bash
cd contracts
nvm use
cp .env.example .env
npm install
npm run fixture:merkle
npm test
```

`test/fixtures/merkle-v1.json` is generated with OpenZeppelin `StandardMerkleTree`. The fixture includes deterministic one-, two-, three-, and five-leaf cases, encoded ABI leaf values, leaf hashes, sorted leaves, roots, proofs, and sorted leaf indices. Java must reproduce these bytes exactly.

### Development toolchain security

The current Hardhat 2 dependency graph reports high-severity advisories in development-only transitive packages. Those packages are not included in the Spring application or deployed contract bytecode, but the toolchain must still be treated as untrusted build infrastructure:

- Run Hardhat only from this trusted repository in an isolated local or CI environment.
- Use a supported Node.js LTS release and the committed lockfile.
- Never process untrusted archives, source trees, plugins, or RPC responses with deployment credentials present.
- Do not expose `.env`, deployment keys, or issuer keys to pull-request jobs.
- Migrate to Hardhat 3 and resolve or explicitly approve every remaining high-severity advisory before Kaia Mainnet deployment.

`npm audit fix --force` is not an accepted remediation because it currently proposes incompatible package changes. Toolchain migration must be performed as a separate tested change.

## Kairos deployment

Current V1 testnet deployment:

- Chain ID: `1001`
- Registry: [`0x4ca738CC22Af5aE40EA8A23E001FA93e1e044117`](https://kairos.kaiascan.io/address/0x4ca738CC22Af5aE40EA8A23E001FA93e1e044117)
- Deployment manifest: [`deployments/kairos-1001.json`](./deployments/kairos-1001.json)
- Human-readable evidence and procedure: [`../docs/blockchain-kairos-deployment.md`](../docs/blockchain-kairos-deployment.md)

1. Fund the deployment account with Kairos test KAIA.
2. Put only the public deployment values in `contracts/.env`:

```dotenv
DEPLOYER_ADDRESS=0x...
ISSUER_PUBLIC_ID=<organization.publicId>
ISSUER_KEY_VERSION=1
ISSUER_SIGNER_ADDRESS=0x...
RELAYER_ADDRESS=0x...
RELAYER_TARGET_BALANCE_KAIA=1
```

`ISSUER_KEY_VERSION=1` is the fresh-issuer default. When operating the committed Kairos
deployment, use the active version from `deployments/kairos-1001.json`, currently version `2`.

3. Start the loopback-only deployment console:

```bash
npm run deploy:kairos:wallet
```

4. Open `http://127.0.0.1:4173` in the Chrome profile that has Kaia Wallet installed.
5. Connect the expected deployer on Kairos and approve the registry deployment transaction.
6. Prove ownership of the configured issuer signer. When the signer is in Kaia Wallet, switch to it, reconnect, and sign the EIP-712 ownership proof. This signature does not spend gas.
   When the test signer is held in the macOS Keychain helper instead, start the console with the proof injected as a public, contract-bound value:

```bash
ISSUER_PROOF_SIGNATURE="$(./scripts/kairos-e2e-keychain.sh issuer-proof)" \
  npm run deploy:kairos:wallet
```

   The helper passes the private key to a short-lived Node process over standard input. Only the EIP-712 signature reaches the loopback page, and the page independently recovers and checks the configured signer before enabling registration.
7. Switch back to the deployer, reconnect, and run `Relayer bootstrap`. The console checks `RELAYER_ROLE`, grants it only when missing, then reads the relayer's native KAIA balance.
8. If the relayer is below `RELAYER_TARGET_BALANCE_KAIA`, the console computes only the shortfall and checks chain ID `1001`, the expected deployer, a nonzero relayer, the 5 KAIA hard cap, and the deployer's balance plus estimated gas before opening the Wallet approval for the native transfer. If the target is already met, no funding transaction is created.
9. Run `Issuer signer 등록` to approve the issuer-key registration transaction, then run the on-chain verification. Every role, registration, and funding transaction is separately approved by the connected deployer in Kaia Wallet.
10. Copy the resulting registry address to `REGISTRY_ADDRESS` in `contracts/.env` and to the backend's `BLOCKCHAIN_CONTRACT_ADDRESS`.

The console accepts only the configured deployer and issuer signer accounts. It persists the deployment transaction hash and predicted contract address in browser storage as soon as the transaction is broadcast, then recovers the result after refresh instead of allowing an accidental duplicate deployment.

The local server has an explicit asset allowlist. It serves the UI, compiled contract artifact, local ethers bundle, and public deployment configuration only. It cannot serve `.env`, private keys, arbitrary `node_modules`, or parent paths.

`RELAYER_TARGET_BALANCE_KAIA` is intentionally public UI configuration, not a credential. An explicitly supplied `ISSUER_PROOF_SIGNATURE` is also public: it is bound to chain ID, Registry, issuer ID, signer, and key version and is revalidated in the browser. The server never exposes `DEPLOYER_PRIVATE_KEY`, `RELAYER_PRIVATE_KEY`, `ISSUER_PRIVATE_KEY`, or seed phrases. The target defaults to `1` KAIA and the browser rejects values outside `(0, 5]` KAIA or with more than 18 decimal places.

For non-interactive Kairos CI or isolated development environments, the private-key Hardhat path remains available:

```bash
DEPLOYER_PRIVATE_KEY=0x...
npm run deploy:kairos
```

Never place a mainnet or production key in a plaintext `.env`. The CLI path is limited to disposable Kairos accounts. After a CLI deployment, set `REGISTRY_ADDRESS`, `ISSUER_PUBLIC_ID`, `ISSUER_KEY_VERSION`, `ISSUER_SIGNER_ADDRESS`, and `RELAYER_ADDRESS`, then run:

```bash
npm run configure:kairos
```

For Kairos-only integration tests, `npm run sign:approval` signs the typed-data file named by `TYPED_DATA_FILE` with `ISSUER_PRIVATE_KEY`. Production issuer keys must remain in an external wallet, KMS, or HSM.

After registering a Keychain-held Kairos issuer, run the read path separately before enabling writes:

```bash
./scripts/run-kairos-read-only.sh
```

This starts Spring in `READ_ONLY`, persists the configured UUID as a real `Organization.publicId`,
derives the issuer ID through the production service, and checks the signer returned by Kairos.
It deliberately unsets the relayer key. The write E2E is a separate opt-in command:

```bash
KAIROS_LIVE_E2E_CONFIRM=I_UNDERSTAND_KAIROS_WRITES \
  ./scripts/run-kairos-live-e2e.sh
```

That test uses the production issuance, Merkle, outbox, Web3j, receipt reconciliation, public
verification, revoke, and supersede services against Kairos. Both scripts read disposable test keys
from macOS Keychain and never print them. The write wrapper first checks the committed Registry
address, runtime hash, roles, active issuer key, relayer, and balance. It then passes keys only to
the short-lived no-daemon JVM process.

Public Registry state can be checked without any wallet or private key:

```bash
npm run read:kairos
```

The command fails unless the runtime code exists and the configured deployer roles, relayer role,
and issuer signer version all match. Its JSON output contains only public addresses, role results,
and the relayer balance.

Kairos is configured with chain ID `1001`. Source verification uses the Kaiascan Hardhat custom chain configuration:

```bash
npm run verify:kairos -- <contract-address> <initial-admin-address>
```

The configured API is `https://kairos-api.kaiascan.io/hardhat-verify` and the browser URL is `https://kairos.kaiascan.io`. Set `KAIASCAN_API_KEY` when Kaiascan requires it.

## Production handoff

The deployer temporarily receives every role. Before production anchoring, grant `DEFAULT_ADMIN_ROLE` and `ISSUER_KEY_ADMIN_ROLE` to the institution's multisig/timelock, grant `RELAYER_ROLE` only to the operated relayer, grant `PAUSER_ROLE` to the incident-response authority, then remove operational roles from the deployer after confirming the replacement grants.

Each issuer signer is registered by version. The registered value is the secp256k1 address recovered from the EIP-712 signature, not necessarily a Kaia account address. For Kaia role-based or decoupled account keys, register the public-key-derived EVM address or introduce a separate `validateSender` contract version. The standard web3j relayer must use an `AccountKeyLegacy` EOA.

Retiring or compromising a signer blocks new approvals from that key and never mutates an anchored root. Normal rotation keeps anchors recorded before `validUntil` valid. A backdated `compromisedAt` intentionally makes anchors and status records at or after the incident time fail issuer validation. Store approval payloads, signatures, receipt data, proofs, and the contract address off-chain for later verification.
