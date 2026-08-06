-- ReviewRound forward migration for a database created from origin/develop
-- before the final ERD transition.
--
-- Preconditions:
--   1. Back up the database.
--   2. Stop application writes.
--   3. Run 2026-07-27-remove-submission-version-state.sql first when needed.
--   4. Do not run this script when review_round already contains data.
--   5. Run with MySQL 8.0+ (round_no uses ROW_NUMBER()).
--
-- The old review columns remain nullable for one deployment only for
-- post-migration verification. A plain application rollback is safe only
-- before the new application writes ReviewRound data; afterward use a backup
-- restore or a verified down-migration because this release does not dual-write
-- the legacy relations.
-- MySQL DDL performs implicit commits, so this script is not fully atomic.
-- Restore the backup if any statement fails after the preflight checks.

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

DROP PROCEDURE IF EXISTS assert_review_round_migration_ready;

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
          AND target_type IS NOT NULL
          AND target_type NOT IN (
              'ALL_SUBMISSIONS',
              'PREVIOUS_PASSED',
              'MANUAL'
          )
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'review stage has an unsupported target_type';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM contest_stage
        WHERE stage_type IN ('REVIEW', 'PRESENTATION')
          AND (pass_rule IS NULL OR pass_rule = 'FINAL')
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'map legacy NULL or FINAL pass_rule before migration';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM contest_stage
        WHERE stage_type IN ('REVIEW', 'PRESENTATION')
          AND pass_rule NOT IN ('TOP_N', 'MIN_SCORE', 'MANUAL')
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'review stage has an unsupported pass_rule';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM contest_stage
        WHERE stage_type IN ('REVIEW', 'PRESENTATION')
          AND (
              (
                  pass_rule = 'TOP_N'
                  AND (
                      pass_count IS NULL
                      OR pass_count <= 0
                      OR min_score IS NOT NULL
                  )
              )
              OR (
                  pass_rule = 'MIN_SCORE'
                  AND (
                      min_score IS NULL
                      OR min_score < 0
                      OR pass_count IS NOT NULL
                  )
              )
              OR (
                  pass_rule = 'MANUAL'
                  AND (pass_count IS NOT NULL OR min_score IS NOT NULL)
              )
          )
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'review stage has an invalid decision configuration';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM contest_stage
        WHERE stage_type IN ('REVIEW', 'PRESENTATION')
          AND status NOT IN ('PREPARING', 'OPEN', 'COMPLETED')
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'review stage has an unsupported status';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM contest_stage
        WHERE stage_type IN ('REVIEW', 'PRESENTATION')
          AND status = 'OPEN'
          AND target_type = 'MANUAL'
          AND pass_rule = 'MANUAL'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'map open MANUAL/MANUAL stage before migration';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM contest_stage
        WHERE stage_type IN ('REVIEW', 'PRESENTATION')
          AND status = 'OPEN'
          AND ends_at <= NOW(6)
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'open review stage is already expired';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM contest_stage
        WHERE stage_type IN ('REVIEW', 'PRESENTATION')
          AND status = 'OPEN'
        GROUP BY contest_id
        HAVING COUNT(*) > 1
        LIMIT 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'contest has multiple open review stages';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM contest_stage later_stage
        JOIN contest_stage earlier_stage
          ON earlier_stage.contest_id = later_stage.contest_id
         AND earlier_stage.stage_type IN ('REVIEW', 'PRESENTATION')
         AND (
             earlier_stage.sequence_no < later_stage.sequence_no
             OR (
                 earlier_stage.sequence_no = later_stage.sequence_no
                 AND earlier_stage.id < later_stage.id
             )
         )
        WHERE later_stage.stage_type IN ('REVIEW', 'PRESENTATION')
          AND later_stage.status IN ('OPEN', 'COMPLETED')
          AND earlier_stage.status <> 'COMPLETED'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'review stage lifecycle is not sequential';
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
        FROM contest_stage_entry
        WHERE status NOT IN (
            'ELIGIBLE',
            'IN_REVIEW',
            'PASSED',
            'FAILED',
            'WITHDRAWN',
            'DISQUALIFIED'
        )
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'review entry has an unsupported status';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM contest_stage_entry
        WHERE decision_type IS NOT NULL
          AND decision_type NOT IN ('RULE', 'MANUAL')
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'review entry has an unsupported decision_type';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM review_assignment
        WHERE status NOT IN ('ASSIGNED', 'COMPLETED', 'CANCELED')
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'review assignment has an unsupported status';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM review_assignment assignment
        WHERE (
            assignment.status = 'COMPLETED'
            AND NOT EXISTS (
                SELECT 1
                FROM review
                WHERE review.assignment_id = assignment.id
            )
        )
        OR (
            assignment.status <> 'COMPLETED'
            AND EXISTS (
                SELECT 1
                FROM review
                WHERE review.assignment_id = assignment.id
            )
        )
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'review assignment status does not match review existence';
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

    IF EXISTS (
        SELECT 1
        FROM contest_stage_entry entry
        JOIN contest_stage stage
          ON stage.id = entry.contest_stage_id
        JOIN submission
          ON submission.id = entry.submission_id
        JOIN team
          ON team.id = submission.team_id
        WHERE stage.contest_id <> team.contest_id
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'review entry submission belongs to another contest';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM contest_stage_entry entry
        JOIN contest_stage stage
          ON stage.id = entry.contest_stage_id
        JOIN submission
          ON submission.id = entry.submission_id
        JOIN team
          ON team.id = submission.team_id
        WHERE stage.stage_type IN ('REVIEW', 'PRESENTATION')
          AND stage.status IN ('OPEN', 'COMPLETED')
          AND team.participation_finalized_at IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'review entry team participation is not finalized';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM review_assignment assignment
        JOIN contest_judge judge
          ON judge.id = assignment.contest_judge_id
        JOIN contest_stage_entry entry
          ON entry.id = assignment.contest_stage_entry_id
        JOIN contest_stage stage
          ON stage.id = entry.contest_stage_id
        WHERE judge.contest_id <> stage.contest_id
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'review assignment judge belongs to another contest';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM contest
        JOIN contest_stage stage
          ON stage.contest_id = contest.id
        WHERE contest.status = 'AWARDED'
          AND stage.stage_type IN ('REVIEW', 'PRESENTATION')
          AND stage.status <> 'COMPLETED'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'awarded contest has an unfinished review stage';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM contest
        WHERE contest.status = 'AWARDED'
          AND NOT EXISTS (
              SELECT 1
              FROM award
              JOIN team ON team.id = award.team_id
              WHERE team.contest_id = contest.id
                AND award.status = 'CONFIRMED'
                AND award.confirmed_at IS NOT NULL
          )
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'awarded contest has no confirmed award';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM award
        JOIN contest_stage_entry entry
          ON entry.id = award.contest_stage_entry_id
        JOIN submission
          ON submission.id = entry.submission_id
        WHERE award.team_id <> submission.team_id
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'award team does not match its review entry';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM award
        JOIN contest_stage_entry entry
          ON entry.id = award.contest_stage_entry_id
        JOIN contest_stage awarded_stage
          ON awarded_stage.id = entry.contest_stage_id
        WHERE awarded_stage.stage_type IN ('REVIEW', 'PRESENTATION')
          AND EXISTS (
              SELECT 1
              FROM contest_stage later_stage
              WHERE later_stage.contest_id = awarded_stage.contest_id
                AND later_stage.stage_type IN ('REVIEW', 'PRESENTATION')
                AND (
                    later_stage.sequence_no
                        > awarded_stage.sequence_no
                    OR (
                        later_stage.sequence_no
                            = awarded_stage.sequence_no
                        AND later_stage.id > awarded_stage.id
                    )
                )
          )
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'award does not reference the final review stage';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM contest_judge
        WHERE user_id IS NOT NULL
        GROUP BY contest_id, user_id
        HAVING COUNT(*) > 1
        LIMIT 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'duplicate internal judge exists in a contest';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM contest_stage stage
        WHERE stage.stage_type IN ('REVIEW', 'PRESENTATION')
          AND stage.status IN ('OPEN', 'COMPLETED')
          -- A fully decided MANUAL/MANUAL legacy round did not require
          -- a scoring rubric or judge review.
          AND NOT (
              stage.status = 'COMPLETED'
              AND stage.target_type = 'MANUAL'
              AND stage.pass_rule = 'MANUAL'
          )
          AND NOT EXISTS (
              SELECT 1
              FROM review_criterion criterion
              WHERE criterion.contest_stage_id = stage.id
                AND criterion.active = TRUE
                AND criterion.max_score > 0
          )
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'open or completed review stage has no valid criterion';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM contest_stage stage
        WHERE stage.stage_type IN ('REVIEW', 'PRESENTATION')
          AND stage.status IN ('OPEN', 'COMPLETED')
          AND NOT EXISTS (
              SELECT 1
              FROM contest_stage_entry entry
              WHERE entry.contest_stage_id = stage.id
          )
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'open or completed review stage has no entry';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM contest_stage_entry entry
        JOIN contest_stage stage ON stage.id = entry.contest_stage_id
        WHERE stage.stage_type IN ('REVIEW', 'PRESENTATION')
          AND stage.status = 'OPEN'
          AND entry.status <> 'IN_REVIEW'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'open review stage has an entry outside IN_REVIEW';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM contest_stage_entry entry
        JOIN contest_stage stage
          ON stage.id = entry.contest_stage_id
        WHERE stage.stage_type IN ('REVIEW', 'PRESENTATION')
          AND stage.status = 'COMPLETED'
          AND stage.target_type = 'MANUAL'
          AND stage.pass_rule = 'MANUAL'
          AND entry.final_score IS NOT NULL
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'manual no-review stage contains a score';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM contest_stage_entry entry
        JOIN contest_stage stage ON stage.id = entry.contest_stage_id
        WHERE stage.stage_type IN ('REVIEW', 'PRESENTATION')
          AND stage.status = 'COMPLETED'
          AND (
              entry.status NOT IN (
                  'PASSED',
                  'FAILED',
                  'WITHDRAWN',
                  'DISQUALIFIED'
              )
              OR entry.finalized_at IS NULL
	              OR (
	                  entry.status IN ('PASSED', 'FAILED')
	                  AND (
	                      (
	                          entry.final_score IS NULL
	                          AND NOT (
	                              stage.target_type = 'MANUAL'
	                              AND stage.pass_rule = 'MANUAL'
	                              AND entry.decision_type = 'MANUAL'
	                          )
	                      )
	                      OR entry.rank_no IS NULL
	                      OR entry.rank_no <= 0
	                      OR entry.decision_type IS NULL
                      OR entry.decision_type NOT IN ('RULE', 'MANUAL')
                  )
              )
              OR (
                  entry.decision_type = 'MANUAL'
                  AND (
                      entry.decided_by_user_id IS NULL
                      OR entry.decision_reason IS NULL
                      OR TRIM(entry.decision_reason) = ''
                  )
              )
          )
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'completed review stage has an unfinished result';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM contest_stage
        WHERE stage_type IN ('REVIEW', 'PRESENTATION')
          AND status = 'COMPLETED'
          AND COALESCE(updated_at, created_at) IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000'
	            SET MESSAGE_TEXT = 'completed review stage has no finalization timestamp';
	    END IF;

	    IF EXISTS (
	        SELECT 1
	        FROM (
	            SELECT stage.id
	            FROM contest_stage stage
	            JOIN contest_stage_entry entry
	              ON entry.contest_stage_id = stage.id
	            WHERE stage.stage_type IN ('REVIEW', 'PRESENTATION')
	              AND stage.status = 'COMPLETED'
	              AND entry.status IN ('PASSED', 'FAILED')
	            GROUP BY stage.id
	            HAVING MIN(entry.rank_no) <> 1
	                OR MAX(entry.rank_no) <> COUNT(*)
	                OR COUNT(DISTINCT entry.rank_no) <> COUNT(*)
	        ) invalid_rank
	    ) THEN
	        SIGNAL SQLSTATE '45000'
	            SET MESSAGE_TEXT = 'completed review stage ranks are not contiguous';
	    END IF;

	    IF EXISTS (
	        SELECT 1
	        FROM contest_stage_entry entry
        JOIN contest_stage stage ON stage.id = entry.contest_stage_id
        WHERE stage.stage_type IN ('REVIEW', 'PRESENTATION')
          AND stage.status IN ('OPEN', 'COMPLETED')
          -- Apply the same narrow historical-manual exception as above.
          AND NOT (
              stage.status = 'COMPLETED'
              AND stage.target_type = 'MANUAL'
              AND stage.pass_rule = 'MANUAL'
          )
          AND NOT EXISTS (
              SELECT 1
              FROM review_assignment assignment
              WHERE assignment.contest_stage_entry_id = entry.id
                AND assignment.status <> 'CANCELED'
          )
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'open or completed review entry has no active assignment';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM review_assignment assignment
        JOIN contest_stage_entry entry
          ON entry.id = assignment.contest_stage_entry_id
        JOIN contest_stage stage ON stage.id = entry.contest_stage_id
        WHERE stage.stage_type IN ('REVIEW', 'PRESENTATION')
          AND stage.status = 'COMPLETED'
          AND assignment.status <> 'CANCELED'
          AND (
              assignment.status <> 'COMPLETED'
              OR NOT EXISTS (
                  SELECT 1
                  FROM review
                  WHERE review.assignment_id = assignment.id
              )
          )
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'completed review stage has an incomplete assignment';
    END IF;
END$$

DELIMITER ;

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
    ROW_NUMBER() OVER (
        PARTITION BY stage.contest_id
        ORDER BY stage.sequence_no ASC, stage.id ASC
    ),
    stage.name,
    CASE stage.status
        WHEN 'COMPLETED' THEN 'FINALIZED'
        ELSE stage.status
    END,
    stage.starts_at,
    stage.ends_at,
    CASE
        WHEN stage.target_type IS NULL THEN 'ALL_SUBMISSIONS'
        WHEN stage.target_type = 'PREVIOUS_PASSED'
            THEN 'PREVIOUS_SELECTED'
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

ALTER TABLE award
    ADD COLUMN review_round_entry_id BIGINT NULL;

UPDATE award
SET review_round_entry_id = contest_stage_entry_id;

ALTER TABLE contest_judge
    ADD COLUMN token_issued_at DATETIME(6) NULL,
    ADD COLUMN token_revoked_at DATETIME(6) NULL;

UPDATE contest_judge
SET token_issued_at = COALESCE(
        token_issued_at,
        created_at,
        NOW(6)
    ),
    token_revoked_at = COALESCE(token_revoked_at, NOW(6))
WHERE review_token_hash IS NOT NULL;

-- Assert every nullable backfill and future unique key before tightening any
-- legacy table. A failure here leaves the old FK columns unchanged.
DROP PROCEDURE IF EXISTS assert_review_round_backfill_ready;

DELIMITER $$

CREATE PROCEDURE assert_review_round_backfill_ready()
BEGIN
    IF (
        SELECT COUNT(*)
        FROM review_round
    ) <> (
        SELECT COUNT(*)
        FROM contest_stage
        WHERE stage_type IN ('REVIEW', 'PRESENTATION')
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'review round backfill count does not match';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM review_round
        GROUP BY contest_id
        HAVING MIN(round_no) <> 1
            OR MAX(round_no) <> COUNT(*)
            OR COUNT(DISTINCT round_no) <> COUNT(*)
        LIMIT 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'review round numbers are not contiguous per contest';
    END IF;

    IF (
        SELECT COUNT(*)
        FROM review_round_entry
    ) <> (
        SELECT COUNT(*)
        FROM contest_stage_entry
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'review round entry backfill count does not match';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM review_criterion
        WHERE review_round_id IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'review criterion round backfill is missing';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM review_assignment
        WHERE review_round_entry_id IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'review assignment entry backfill is missing';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM award
        WHERE review_round_entry_id IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'award entry backfill is missing';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM review_criterion
        GROUP BY review_round_id, code
        HAVING COUNT(*) > 1
        LIMIT 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'duplicate criterion exists in a review round';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM review_assignment
        GROUP BY contest_judge_id, review_round_entry_id
        HAVING COUNT(*) > 1
        LIMIT 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'duplicate judge assignment exists for an entry';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM award
        GROUP BY review_round_entry_id
        HAVING COUNT(*) > 1
        LIMIT 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'duplicate award exists for a review entry';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM contest_judge
        WHERE user_id IS NOT NULL
        GROUP BY contest_id, user_id
        HAVING COUNT(*) > 1
        LIMIT 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'duplicate internal judge exists in a contest';
    END IF;
END$$

DELIMITER ;

CALL assert_review_round_backfill_ready();
DROP PROCEDURE assert_review_round_backfill_ready;

ALTER TABLE review_criterion
    MODIFY COLUMN contest_stage_id BIGINT NULL,
    MODIFY COLUMN review_round_id BIGINT NOT NULL,
    ADD CONSTRAINT uk_review_criterion_round_code
        UNIQUE (review_round_id, code),
    ADD CONSTRAINT fk_review_criterion_round
        FOREIGN KEY (review_round_id) REFERENCES review_round (id);

ALTER TABLE review_assignment
    MODIFY COLUMN contest_stage_entry_id BIGINT NULL,
    MODIFY COLUMN review_round_entry_id BIGINT NOT NULL,
    ADD CONSTRAINT uk_review_assignment_judge_entry
        UNIQUE (contest_judge_id, review_round_entry_id),
    ADD CONSTRAINT fk_review_assignment_round_entry
        FOREIGN KEY (review_round_entry_id) REFERENCES review_round_entry (id);

ALTER TABLE award
    MODIFY COLUMN contest_stage_entry_id BIGINT NULL,
    MODIFY COLUMN review_round_entry_id BIGINT NOT NULL,
    ADD CONSTRAINT uk_award_review_round_entry
        UNIQUE (review_round_entry_id),
    ADD CONSTRAINT fk_award_review_round_entry
        FOREIGN KEY (review_round_entry_id) REFERENCES review_round_entry (id);

ALTER TABLE review
    MODIFY COLUMN total_score DECIMAL(12, 2) NOT NULL;

ALTER TABLE review_score_item
    MODIFY COLUMN score DECIMAL(12, 2) NOT NULL;

ALTER TABLE contest_judge
    MODIFY COLUMN name VARCHAR(100) NOT NULL,
    MODIFY COLUMN role_label VARCHAR(100) NOT NULL,
    MODIFY COLUMN review_token_hash VARCHAR(64) NULL,
    MODIFY COLUMN token_expires_at DATETIME(6) NULL,
    ADD CONSTRAINT uk_contest_judge_user
        UNIQUE (contest_id, user_id);

-- Verification queries: every count must be zero before application startup.
SELECT COUNT(*) AS non_contiguous_round_sequence
FROM (
    SELECT contest_id
    FROM review_round
    GROUP BY contest_id
    HAVING MIN(round_no) <> 1
        OR MAX(round_no) <> COUNT(*)
        OR COUNT(DISTINCT round_no) <> COUNT(*)
) invalid_round_sequence;

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
