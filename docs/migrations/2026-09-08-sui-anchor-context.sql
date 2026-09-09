-- MANUAL migration; not run by this change. MySQL 8, maintenance window, worker OFF.
-- Back up the actual database first. DDL auto-commits. Rehearse on a restored copy.
-- Existing signer_address (20-byte institution ECDSA ID) and issuer_signature (65) DO NOT change.
-- Do not rewrite canonical payloads, Merkle V1, existing chain IDs, hashes or approvals.

ALTER TABLE anc_batch ADD COLUMN chain_context VARCHAR(384) NULL;
ALTER TABLE anc_credential_status_event ADD COLUMN chain_context VARCHAR(384) NULL;
ALTER TABLE anc_issuer_key ADD COLUMN chain_context VARCHAR(384) NULL;
ALTER TABLE anc_chain_transaction
    ADD COLUMN chain_context VARCHAR(384) NULL,
    MODIFY COLUMN contract_address VARBINARY(32) NOT NULL,
    MODIFY COLUMN relayer_address VARBINARY(32) NULL;

-- NULL context denotes a historical KAIA record, NEVER a Sui record.
-- Preserve legacy coordinates explicitly where the transaction supplies authoritative values.
UPDATE anc_chain_transaction
SET chain_context = CONCAT('KAIA|', chain_id, '|0x', LOWER(HEX(contract_address)), '|', contract_version)
WHERE chain_context IS NULL AND chain_id > 0 AND OCTET_LENGTH(contract_address) = 20;

UPDATE anc_batch b
JOIN anc_chain_transaction t ON t.batch_id = b.id AND t.operation_type = 'ANCHOR_BATCH'
SET b.chain_context = t.chain_context
WHERE b.chain_context IS NULL AND t.chain_context IS NOT NULL;

UPDATE anc_credential_status_event e
JOIN anc_chain_transaction t ON t.credential_status_event_id = e.id
SET e.chain_context = t.chain_context
WHERE e.chain_context IS NULL AND t.chain_context IS NOT NULL;

-- Do not infer unanchored batch domains or key lifecycle domains from current configuration.
-- Existing keys keep NULL context and are usable ONLY with KAIA. For Sui use an UNUSED keyVersion
-- for each organization; sync creates a new row and never overwrites a legacy key's lifecycle.
-- Sui tx_nonce remains SQL NULL (no artificial EVM nonce). Hash bytes remain native 32-byte values.
-- The existing unique(tx_hash, chain_id) constraint is deliberately conservative across Sui networks:
-- if an identical digest occurs on another network, reservation fails closed instead of overwriting.

-- Verify before activation: no original 20-byte address was zero-padded, each old hex value is identical,
-- row counts agree, original public packages verify on legacy reader, and new H2/MySQL tests pass.
-- Rollback is application/config rollback with these additive/widened columns retained.
-- NEVER shrink addresses to 20 bytes after Sui rows exist; restore a pre-migration DB copy instead.
