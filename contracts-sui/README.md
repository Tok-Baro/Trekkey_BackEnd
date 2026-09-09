# Trekkey Sui credential registry V1

This package adds a hash-only Sui registry. It has local unit-test and ephemeral localnet integration evidence, **not a public deployment or independent security audit**. It does not implement Walrus storage, Seal encryption, student sharing policy, or institution onboarding.

## Preserved data and approval contracts

- Credential Merkle V1 remains the original six 32-byte ABI words, double Keccak leaf, and sorted-pair Keccak tree. `tests/fixture_tests.move` checks the existing 1/2/3/5-leaf Java/OpenZeppelin fixtures.
- `BatchApproval` and `StatusApproval` retain the existing Java EIP-712 **struct hashes**, integer widths, and field order. The enclosing domain is intentionally not an EVM chain/contract domain.
- `domainHash = keccak256(UTF8("TREKKEY_SUI_APPROVAL_V1") || chainIdentifier[4] || packageId[32] || registryId[32])`.
- `digest = keccak256(0x1901 || domainHash || structHash)`. Both Sui IDs are complete 32-byte values. `packageId` is the original defining package ID; it is not an EVM-address truncation.
- The issuer signs with the existing secp256k1 identity: 20-byte Ethereum-derived signer and low-S `r || s || v` signature with external `v = 27/28`. The Sui transaction sender is a separate, allowlisted 32-byte relayer identity.
- Sui's recovery native hashes its message input. Therefore recovery receives the **66-byte** `0x1901 || domainHash || structHash` preimage, with the recovery byte normalized to `0/1`, rather than hashing the digest again.

`test-fixtures/approval-v1.json` is a public synthetic scalar-1 fixture shared with Java and the TypeScript gateway. It is never a deployment or operational key.

## Authority and state

`create_registry` shares a `Registry` and transfers its registry-scoped `AdminCap` to the creator. A fresh registry has no relayers. Only possession of its matching cap allows issuer-key registration, retirement/compromise, relayer changes, or pause changes. Relayers cannot bypass issuer signatures.

The registry stores immutable first-issuance batches, one-way credential status records, issuer keys, used digests, and issuer-scoped nonces shared across batch/status operations and key versions. Tree version 1 is the only accepted tree format. Nonzero hashes, timestamps, key versions, and batch size are checked. Clock milliseconds are converted to seconds. As in the previous EVM V1 contract, approval deadlines and key `validUntil` seconds are inclusive; compromise blocks from its recorded second. Pausing stops issuance but leaves revocation/supersession available.

Tables can be read through Sui dynamic fields; absence means no record, not an empty successful record. Registry BCS field order is:

`id → chain_identifier → package_id → paused → issuer_keys → batches → statuses → used_nonces → used_digests → relayers`.

The exact table key/value structs, operation argument order, and event fields are defined in `sources/credential_registry.move`. Every receipt event starts with `registry_id`. Getters also return `Option` for absent keys, batches, and status records.

## Trust and rollout limits

- The Move VM does not provide this module an intrinsic chain identifier. The deployment operator supplies four bytes once; the gateway must independently compare the real RPC chain, configured package, registry object, and registry fields before reads, preparation, and submission.
- A root-only registry does not prove target/replacement membership or the factual truth of a credential. As with the old contract, approved issuer/Java policy must establish same-issuer, already-anchored replacement membership. The Move contract rejects zero/self replacement and repeated state changes but accepts no inclusion proof for those targets.
- Hashes are public and potentially linkable; hash-only is not a claim of anonymity. Plaintext, student IDs, documents, keys, and sharing secrets must never be passed into these public records or events.
- Institution trust, administrator/cap custody, package-upgrade authority, gas/relayer availability, outbox reconciliation, privacy controls, and production rollout remain separate operational responsibilities. Package-domain binding does not prevent a privileged future package upgrade from changing behavior.
- No mainnet/testnet publication or performance/security audit is established by these tests. An explicit 2026-09-08 ephemeral localnet check exercised publication, registry/issuer/relayer setup, HTTP prepare/persist/broadcast, checkpoint receipts, anchoring and revocation; see [migration evidence](../docs/sui-migration-2026-09-08.md). It did not exercise the browser/Java/production-DB flow. Existing EVM records are not migrated by this package.

## Reproducible local checks

The framework is pinned in `Move.toml` and `Move.lock`. Local validation used Sui CLI `1.79.0-46f18562f1f5`.

Use an explicitly pre-created, **keyless task-local** `SUI_CONFIG_DIR` and a task-local `MOVE_HOME`. Some Sui CLI versions automatically initialize a wallet if configuration is absent; do not run these commands against the user's default config or allow initialization to generate a key.

```sh
SUI_CONFIG_DIR=/absolute/task-local/keyless-config MOVE_HOME=/absolute/task-local/move-cache sui move build --warnings-are-errors
SUI_CONFIG_DIR=/absolute/task-local/keyless-config MOVE_HOME=/absolute/task-local/move-cache sui move test --warnings-are-errors
```

The one intentional composability lint suppression applies only to delivering a newly created `AdminCap` directly to its creator. Test signing uses a public synthetic scalar via test-only native code; it is absent from the production package.
