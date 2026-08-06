-- Backfill public IDs for teams created before Team.publicId was introduced.

UPDATE team
SET public_id = UUID()
WHERE public_id IS NULL
   OR TRIM(public_id) = '';

ALTER TABLE team
    ADD CONSTRAINT uk_team_public_id UNIQUE (public_id);
