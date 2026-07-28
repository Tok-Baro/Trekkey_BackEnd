-- ReviewRound forward migration for a database created from origin/develop
-- before the final ERD transition.
--
-- Preconditions:
--   1. Back up the database.
--   2. Stop application writes.
--   3. Run 2026-07-27-remove-submission-version-state.sql first when needed.
--   4. Do not run this script on a schema that already contains review_round.
--
-- The old review columns remain nullable for one deployment so that a rollback
-- can still read the legacy relations. Remove them only after application and
-- data verification.
-- MySQL DDL performs implicit commits, so this script is not fully atomic.
-- Restore the backup if any statement fails after the preflight checks.

DELIMITER $$

CREATE PROCEDURE assert_review_round_migration_ready()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM review_round
        LIMIT 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'review_round already contains data';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM contest_stage
        WHERE stage_type IN ('REVIEW', 'PRESENTATION')
          AND (starts_at IS NULL OR ends_at IS NULL OR starts_at >= ends_at)
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'review stage has an invalid time window';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM contest_stage
        WHERE stage_type IN ('REVIEW', 'PRESENTATION')
          AND pass_rule = 'FINAL'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'map legacy FINAL pass_rule before migration';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM contest_stage_entry entry
        JOIN contest_stage stage ON stage.id = entry.contest_stage_id
        WHERE stage.stage_type NOT IN ('REVIEW', 'PRESENTATION')
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'stage entry points to a non-review stage';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM review_criterion criterion
        JOIN contest_stage stage ON stage.id = criterion.contest_stage_id
        WHERE stage.stage_type NOT IN ('REVIEW', 'PRESENTATION')
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'review criterion points to a non-review stage';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM contest_stage_entry entry
        LEFT JOIN user actor ON actor.id = entry.decided_by_user_id
        WHERE entry.decided_by_user_id IS NOT NULL
          AND actor.id IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'review decision actor does not exist';
    END IF;
END$$

DELIMITER ;

CREATE TABLE IF NOT EXISTS review_round (
    id BIGINT NOT NULL AUTO_INCREMENT,
    contest_id BIGINT NOT NULL,
    round_no INT NOT NULL,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    decision_rule VARCHAR(30) NOT NULL,
    select_count INT NULL,
    min_score DECIMAL(12, 2) NULL,
    finalized_at DATETIME(6) NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_review_round_contest_round_no
        UNIQUE (contest_id, round_no),
    CONSTRAINT fk_review_round_contest
        FOREIGN KEY (contest_id) REFERENCES contest (id)
);

CALL assert_review_round_migration_ready();
DROP PROCEDURE assert_review_round_migration_ready;

INSERT INTO review_round (
    id,
    contest_id,
    round_no,
    name,
    status,
    starts_at,
    ends_at,
    target_type,
    decision_rule,
    select_count,
    min_score,
    finalized_at,
    created_at,
    updated_at
)
SELECT
    stage.id,
    stage.contest_id,
    stage.sequence_no,
    stage.name,
    CASE stage.status
        WHEN 'COMPLETED' THEN 'FINALIZED'
        ELSE stage.status
    END,
    stage.starts_at,
    stage.ends_at,
    CASE stage.target_type
        WHEN 'PREVIOUS_PASSED' THEN 'PREVIOUS_SELECTED'
        ELSE stage.target_type
    END,
    stage.pass_rule,
    stage.pass_count,
    stage.min_score,
    CASE
        WHEN stage.status = 'COMPLETED'
            THEN COALESCE(stage.updated_at, stage.created_at)
        ELSE NULL
    END,
    stage.created_at,
    stage.updated_at
FROM contest_stage stage
WHERE stage.stage_type IN ('REVIEW', 'PRESENTATION');

CREATE TABLE review_round_entry (
    id BIGINT NOT NULL AUTO_INCREMENT,
    review_round_id BIGINT NOT NULL,
    submission_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    final_score DECIMAL(12, 2) NULL,
    rank_no INT NULL,
    decision_type VARCHAR(20) NULL,
    decided_by_user_id BIGINT NULL,
    decision_reason TEXT NULL,
    finalized_at DATETIME(6) NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_review_round_entry_round_submission
        UNIQUE (review_round_id, submission_id),
    CONSTRAINT fk_review_round_entry_round
        FOREIGN KEY (review_round_id) REFERENCES review_round (id),
    CONSTRAINT fk_review_round_entry_submission
        FOREIGN KEY (submission_id) REFERENCES submission (id),
    CONSTRAINT fk_review_round_entry_actor
        FOREIGN KEY (decided_by_user_id) REFERENCES user (id)
);

INSERT INTO review_round_entry (
    id,
    review_round_id,
    submission_id,
    status,
    final_score,
    rank_no,
    decision_type,
    decided_by_user_id,
    decision_reason,
    finalized_at,
    created_at,
    updated_at
)
SELECT
    entry.id,
    entry.contest_stage_id,
    entry.submission_id,
    CASE entry.status
        WHEN 'PASSED' THEN 'SELECTED'
        WHEN 'FAILED' THEN 'NOT_SELECTED'
        ELSE entry.status
    END,
    entry.final_score,
    entry.rank_no,
    entry.decision_type,
    entry.decided_by_user_id,
    entry.decision_reason,
    entry.finalized_at,
    entry.created_at,
    entry.updated_at
FROM contest_stage_entry entry;

ALTER TABLE review_criterion
    ADD COLUMN review_round_id BIGINT NULL;

UPDATE review_criterion criterion
JOIN review_round round ON round.id = criterion.contest_stage_id
SET criterion.review_round_id = round.id;

ALTER TABLE review_criterion
    MODIFY COLUMN contest_stage_id BIGINT NULL,
    MODIFY COLUMN review_round_id BIGINT NOT NULL,
    ADD CONSTRAINT uk_review_criterion_round_code
        UNIQUE (review_round_id, code),
    ADD CONSTRAINT fk_review_criterion_round
        FOREIGN KEY (review_round_id) REFERENCES review_round (id);

ALTER TABLE review_assignment
    ADD COLUMN review_round_entry_id BIGINT NULL,
    ADD COLUMN due_at DATETIME(6) NULL;

UPDATE review_assignment assignment
JOIN review_round_entry entry
    ON entry.id = assignment.contest_stage_entry_id
JOIN review_round round
    ON round.id = entry.review_round_id
SET assignment.review_round_entry_id = entry.id,
    assignment.due_at = round.ends_at;

ALTER TABLE review_assignment
    MODIFY COLUMN contest_stage_entry_id BIGINT NULL,
    MODIFY COLUMN review_round_entry_id BIGINT NOT NULL,
    ADD CONSTRAINT uk_review_assignment_judge_entry
        UNIQUE (contest_judge_id, review_round_entry_id),
    ADD CONSTRAINT fk_review_assignment_round_entry
        FOREIGN KEY (review_round_entry_id) REFERENCES review_round_entry (id);

ALTER TABLE award
    ADD COLUMN review_round_entry_id BIGINT NULL;

UPDATE award
SET review_round_entry_id = contest_stage_entry_id;

ALTER TABLE award
    MODIFY COLUMN contest_stage_entry_id BIGINT NULL,
    MODIFY COLUMN review_round_entry_id BIGINT NOT NULL,
    ADD CONSTRAINT uk_award_review_round_entry
        UNIQUE (review_round_entry_id),
    ADD CONSTRAINT fk_award_review_round_entry
        FOREIGN KEY (review_round_entry_id) REFERENCES review_round_entry (id);

ALTER TABLE contest_judge
    ADD COLUMN token_issued_at DATETIME(6) NULL,
    ADD COLUMN token_revoked_at DATETIME(6) NULL,
    MODIFY COLUMN review_token_hash VARCHAR(64) NULL,
    MODIFY COLUMN token_expires_at DATETIME(6) NULL;

UPDATE contest_judge
SET token_issued_at = COALESCE(created_at, NOW(6))
WHERE review_token_hash IS NOT NULL
  AND token_issued_at IS NULL;

-- Verification queries: every count must be zero before application startup.
SELECT COUNT(*) AS missing_criterion_round
FROM review_criterion
WHERE review_round_id IS NULL;

SELECT COUNT(*) AS missing_assignment_entry
FROM review_assignment
WHERE review_round_entry_id IS NULL;

SELECT COUNT(*) AS missing_award_entry
FROM award
WHERE review_round_entry_id IS NULL;

SELECT COUNT(*) AS unmapped_legacy_entry
FROM contest_stage_entry legacy
LEFT JOIN review_round_entry migrated ON migrated.id = legacy.id
WHERE migrated.id IS NULL;
