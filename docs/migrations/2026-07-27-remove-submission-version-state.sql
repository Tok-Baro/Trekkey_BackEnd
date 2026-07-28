-- Manual migration for databases created before the simplified submission model.
-- New databases generated from the current entities do not need this script.

ALTER TABLE team
    DROP COLUMN source_version;

ALTER TABLE submission
    DROP COLUMN source_version,
    DROP COLUMN integrity_status;

ALTER TABLE award
    DROP COLUMN source_version;
