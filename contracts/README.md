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

1. Fund the deployment account with Kairos test KAIA.
2. Set `DEPLOYER_PRIVATE_KEY` in `contracts/.env`. Do not place a private key in source control, tickets, or logs.
3. Optionally override `KAIROS_RPC_URL`. The default is Kaia's official public endpoint `https://public-en-kairos.node.kaia.io`.
4. Deploy:

```bash
npm run deploy:kairos
```

After deployment, set `REGISTRY_ADDRESS`, `ISSUER_PUBLIC_ID`, `ISSUER_KEY_VERSION`, `ISSUER_SIGNER_ADDRESS`, and `RELAYER_ADDRESS`, then run:

```bash
npm run configure:kairos
```

For Kairos-only integration tests, `npm run sign:approval` signs the typed-data file named by `TYPED_DATA_FILE` with `ISSUER_PRIVATE_KEY`. Production issuer keys must remain in an external wallet, KMS, or HSM.

Kairos is configured with chain ID `1001`. Source verification uses the Kaiascan Hardhat custom chain configuration:

```bash
npm run verify:kairos -- <contract-address> <initial-admin-address>
```

The configured API is `https://kairos-api.kaiascan.io/hardhat-verify` and the browser URL is `https://kairos.kaiascan.io`. Set `KAIASCAN_API_KEY` when Kaiascan requires it.

## Production handoff

The deployer temporarily receives every role. Before production anchoring, grant `DEFAULT_ADMIN_ROLE` and `ISSUER_KEY_ADMIN_ROLE` to the institution's multisig/timelock, grant `RELAYER_ROLE` only to the operated relayer, grant `PAUSER_ROLE` to the incident-response authority, then remove operational roles from the deployer after confirming the replacement grants.

Each issuer signer is registered by version. The registered value is the secp256k1 address recovered from the EIP-712 signature, not necessarily a Kaia account address. For Kaia role-based or decoupled account keys, register the public-key-derived EVM address or introduce a separate `validateSender` contract version. The standard web3j relayer must use an `AccountKeyLegacy` EOA.

Retiring or compromising a signer blocks new approvals from that key and never mutates an anchored root. Normal rotation keeps anchors recorded before `validUntil` valid. A backdated `compromisedAt` intentionally makes anchors and status records at or after the incident time fail issuer validation. Store approval payloads, signatures, receipt data, proofs, and the contract address off-chain for later verification.
