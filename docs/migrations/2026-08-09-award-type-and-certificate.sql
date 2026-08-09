-- Award type and collision-free certificate number migration.
--
-- Preconditions:
--   1. Back up the database.
--   2. Stop application writes.
--   3. Run this script before switching Hibernate ddl-auto to validate.
--
-- This script is for databases created before Award.awardType was added.
-- New databases generated from the current entities do not need it.

SELECT COUNT(*) INTO @award_type_exists
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'award'
  AND column_name = 'award_type';

SET @add_award_type_sql = IF(
    @award_type_exists = 0,
    'ALTER TABLE award ADD COLUMN award_type VARCHAR(30) NULL AFTER prize',
    'SELECT 1'
);
PREPARE add_award_type_statement FROM @add_award_type_sql;
EXECUTE add_award_type_statement;
DEALLOCATE PREPARE add_award_type_statement;

UPDATE award
SET award_type = CASE prize
    WHEN '대상' THEN 'GRAND_PRIZE'
    WHEN '최우수상' THEN 'EXCELLENCE'
    WHEN '우수상' THEN 'MERIT'
    WHEN '장려상' THEN 'ENCOURAGEMENT'
    WHEN '입선' THEN 'HONORABLE_MENTION'
    WHEN '특별상' THEN 'SPECIAL'
    WHEN '총장상' THEN 'PRESIDENT_AWARD'
    ELSE 'CUSTOM'
END
WHERE award_type IS NULL;

ALTER TABLE award
    MODIFY COLUMN award_type VARCHAR(30) NOT NULL,
    MODIFY COLUMN certificate_no VARCHAR(64) NOT NULL;

SELECT COUNT(*) AS invalid_award_type_count
FROM award
WHERE award_type NOT IN (
    'GRAND_PRIZE',
    'EXCELLENCE',
    'MERIT',
    'ENCOURAGEMENT',
    'HONORABLE_MENTION',
    'SPECIAL',
    'PRESIDENT_AWARD',
    'CUSTOM'
);
