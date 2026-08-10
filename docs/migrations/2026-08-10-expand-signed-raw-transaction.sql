-- Allow the relayer to persist a complete signed EVM transaction before broadcast.
--
-- Hibernate/MySQL may map a byte[] @Lob to TINYBLOB when no SQL type is
-- specified. TINYBLOB is limited to 255 bytes, which is too small for an
-- anchorBatch transaction. BLOB supports up to 64 KiB and is sufficient for
-- every registry operation in contract V1.

ALTER TABLE anc_chain_transaction
    MODIFY COLUMN signed_raw_transaction BLOB NULL;

SELECT data_type, character_octet_length
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'anc_chain_transaction'
  AND column_name = 'signed_raw_transaction';
