#!/usr/bin/env bash
set -euo pipefail

readonly MIGRATION_DATABASE="trekkey_migration_test"
readonly MIGRATION_FIXTURE="docs/migrations/fixtures/2026-07-28-review-round-transition-legacy.sql"
readonly MIGRATION_SCRIPT="docs/migrations/2026-07-28-review-round-transition.sql"
readonly MYSQL_HOST_VALUE="${MYSQL_TEST_HOST:-127.0.0.1}"
readonly MYSQL_PORT_VALUE="${MYSQL_TEST_PORT:-3306}"
readonly MYSQL_USER_VALUE="${MYSQL_TEST_USERNAME:-root}"

export MYSQL_PWD="${MYSQL_TEST_PASSWORD:-root}"

mysql_command=(
  mysql
  --host="${MYSQL_HOST_VALUE}"
  --port="${MYSQL_PORT_VALUE}"
  --user="${MYSQL_USER_VALUE}"
  --batch
  --skip-column-names
)

assert_equals() {
  local expected="$1"
  local actual="$2"
  local label="$3"

  if [[ "${actual}" != "${expected}" ]]; then
    echo "${label}: expected '${expected}', got '${actual}'" >&2
    exit 1
  fi
}

"${mysql_command[@]}" -e \
  "DROP DATABASE IF EXISTS ${MIGRATION_DATABASE};
   CREATE DATABASE ${MIGRATION_DATABASE}
     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

"${mysql_command[@]}" "${MIGRATION_DATABASE}" < "${MIGRATION_FIXTURE}"
"${mysql_command[@]}" "${MIGRATION_DATABASE}" < "${MIGRATION_SCRIPT}"

rounds="$("${mysql_command[@]}" "${MIGRATION_DATABASE}" -e "
  SELECT GROUP_CONCAT(
      CONCAT(id, ':', round_no, ':', target_type, ':', decision_rule)
      ORDER BY round_no SEPARATOR ',')
  FROM review_round;
")"
assert_equals \
  "1000:1:ALL_SUBMISSIONS:TOP_N,1001:2:MANUAL:MANUAL" \
  "${rounds}" \
  "review round backfill"

manual_result="$("${mysql_command[@]}" "${MIGRATION_DATABASE}" -e "
  SELECT CONCAT(
      status,
      ':',
      IF(final_score IS NULL, 'NULL', final_score),
      ':',
      rank_no,
      ':',
      decision_type)
  FROM review_round_entry
  WHERE id = 3001;
")"
assert_equals \
  "SELECTED:NULL:1:MANUAL" \
  "${manual_result}" \
  "manual no-score result"

finalized_rounds="$("${mysql_command[@]}" "${MIGRATION_DATABASE}" -e "
  SELECT GROUP_CONCAT(
      CONCAT(
        id,
        ':',
        status,
        ':',
        DATE_FORMAT(finalized_at, '%Y-%m-%d %H:%i:%s'))
      ORDER BY round_no SEPARATOR ',')
  FROM review_round;
")"
assert_equals \
  "1000:FINALIZED:2026-07-02 00:00:00,1001:FINALIZED:2026-07-04 00:00:00" \
  "${finalized_rounds}" \
  "round lifecycle backfill"

assignment_backfill="$("${mysql_command[@]}" "${MIGRATION_DATABASE}" -e "
  SELECT CONCAT(
      review_round_entry_id,
      ':',
      DATE_FORMAT(due_at, '%Y-%m-%d %H:%i:%s'))
  FROM review_assignment
  WHERE id = 5000;
")"
assert_equals \
  "3000:2026-07-02 00:00:00" \
  "${assignment_backfill}" \
  "assignment backfill"

award_backfill="$("${mysql_command[@]}" "${MIGRATION_DATABASE}" -e "
  SELECT review_round_entry_id
  FROM award
  WHERE id = 8000;
")"
assert_equals "3001" "${award_backfill}" "award backfill"

judge_link_backfill="$("${mysql_command[@]}" "${MIGRATION_DATABASE}" -e "
  SELECT IF(
      token_issued_at IS NOT NULL
        AND token_revoked_at IS NOT NULL,
      'REVOKED',
      'INVALID')
  FROM contest_judge
  WHERE id = 4000;
")"
assert_equals \
  "REVOKED" \
  "${judge_link_backfill}" \
  "legacy judge link revocation"

new_fk_nullability="$("${mysql_command[@]}" "${MIGRATION_DATABASE}" -e "
  SELECT GROUP_CONCAT(
      CONCAT(table_name, '.', column_name, ':', is_nullable)
      ORDER BY table_name, column_name SEPARATOR ',')
  FROM information_schema.columns
  WHERE table_schema = '${MIGRATION_DATABASE}'
    AND (
      (table_name = 'award'
        AND column_name = 'review_round_entry_id')
      OR (table_name = 'review_assignment'
        AND column_name = 'review_round_entry_id')
      OR (table_name = 'review_criterion'
        AND column_name = 'review_round_id')
    );
")"
assert_equals \
  "award.review_round_entry_id:NO,review_assignment.review_round_entry_id:NO,review_criterion.review_round_id:NO" \
  "${new_fk_nullability}" \
  "new relation nullability"

score_precision="$("${mysql_command[@]}" "${MIGRATION_DATABASE}" -e "
  SELECT GROUP_CONCAT(
      CONCAT(table_name, ':', numeric_precision, ':', numeric_scale)
      ORDER BY table_name SEPARATOR ',')
  FROM information_schema.columns
  WHERE table_schema = '${MIGRATION_DATABASE}'
    AND (
      (table_name = 'review' AND column_name = 'total_score')
      OR (table_name = 'review_score_item' AND column_name = 'score')
    );
")"
assert_equals \
  "review:12:2,review_score_item:12:2" \
  "${score_precision}" \
  "score precision"

required_constraints="$("${mysql_command[@]}" "${MIGRATION_DATABASE}" -e "
  SELECT COUNT(*)
  FROM information_schema.table_constraints
  WHERE constraint_schema = '${MIGRATION_DATABASE}'
    AND constraint_name IN (
      'uk_contest_judge_user',
      'uk_review_assignment_judge_entry',
      'uk_review_criterion_round_code',
      'uk_review_round_contest_round_no',
      'uk_review_round_entry_round_submission'
    );
")"
assert_equals "5" "${required_constraints}" "new unique constraints"

"${mysql_command[@]}" -e \
  "DROP DATABASE ${MIGRATION_DATABASE};
   CREATE DATABASE ${MIGRATION_DATABASE}
     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
"${mysql_command[@]}" "${MIGRATION_DATABASE}" < "${MIGRATION_FIXTURE}"
"${mysql_command[@]}" "${MIGRATION_DATABASE}" -e \
  "UPDATE contest_stage_entry SET rank_no = 2 WHERE id = 3001;"

if rank_error="$("${mysql_command[@]}" "${MIGRATION_DATABASE}" \
  < "${MIGRATION_SCRIPT}" 2>&1)"; then
  echo "non-contiguous legacy ranks were accepted" >&2
  exit 1
fi
if [[ "${rank_error}" != *"completed review stage ranks are not contiguous"* ]]; then
  echo "rank rehearsal failed for an unexpected reason" >&2
  exit 1
fi

"${mysql_command[@]}" -e \
  "DROP DATABASE ${MIGRATION_DATABASE};
   CREATE DATABASE ${MIGRATION_DATABASE}
     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
"${mysql_command[@]}" "${MIGRATION_DATABASE}" < "${MIGRATION_FIXTURE}"
"${mysql_command[@]}" "${MIGRATION_DATABASE}" -e \
  "UPDATE contest_stage SET status = 'OPEN' WHERE id = 1001;"

if manual_open_error="$("${mysql_command[@]}" "${MIGRATION_DATABASE}" \
  < "${MIGRATION_SCRIPT}" 2>&1)"; then
  echo "open MANUAL/MANUAL legacy stage was accepted" >&2
  exit 1
fi
if [[ "${manual_open_error}" != *"map open MANUAL/MANUAL stage before migration"* ]]; then
  echo "manual-open rehearsal failed for an unexpected reason" >&2
  exit 1
fi

"${mysql_command[@]}" -e \
  "DROP DATABASE ${MIGRATION_DATABASE};
   CREATE DATABASE ${MIGRATION_DATABASE}
     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
"${mysql_command[@]}" "${MIGRATION_DATABASE}" < "${MIGRATION_FIXTURE}"
"${mysql_command[@]}" "${MIGRATION_DATABASE}" -e \
  "UPDATE award SET team_id = 900 WHERE id = 8000;"

if award_scope_error="$("${mysql_command[@]}" "${MIGRATION_DATABASE}" \
  < "${MIGRATION_SCRIPT}" 2>&1)"; then
  echo "award with a mismatched team was accepted" >&2
  exit 1
fi
if [[ "${award_scope_error}" != *"award team does not match its review entry"* ]]; then
  echo "award-scope rehearsal failed for an unexpected reason" >&2
  exit 1
fi

"${mysql_command[@]}" -e \
  "DROP DATABASE ${MIGRATION_DATABASE};
   CREATE DATABASE ${MIGRATION_DATABASE}
     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
"${mysql_command[@]}" "${MIGRATION_DATABASE}" < "${MIGRATION_FIXTURE}"
"${mysql_command[@]}" "${MIGRATION_DATABASE}" -e \
  "UPDATE award
   SET contest_stage_entry_id = 3000,
       team_id = 900
   WHERE id = 8000;"

if award_source_error="$("${mysql_command[@]}" "${MIGRATION_DATABASE}" \
  < "${MIGRATION_SCRIPT}" 2>&1)"; then
  echo "award based on a non-final review stage was accepted" >&2
  exit 1
fi
if [[ "${award_source_error}" != *"award does not reference the final review stage"* ]]; then
  echo "award-source rehearsal failed for an unexpected reason" >&2
  exit 1
fi

"${mysql_command[@]}" -e \
  "DROP DATABASE ${MIGRATION_DATABASE};
   CREATE DATABASE ${MIGRATION_DATABASE}
     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
"${mysql_command[@]}" "${MIGRATION_DATABASE}" < "${MIGRATION_FIXTURE}"
"${mysql_command[@]}" "${MIGRATION_DATABASE}" -e \
  "INSERT INTO contest (id, status) VALUES (2, 'REVIEWING');
   UPDATE team SET contest_id = 2 WHERE id = 900;"

if entry_scope_error="$("${mysql_command[@]}" "${MIGRATION_DATABASE}" \
  < "${MIGRATION_SCRIPT}" 2>&1)"; then
  echo "review entry with a cross-contest submission was accepted" >&2
  exit 1
fi
if [[ "${entry_scope_error}" != *"review entry submission belongs to another contest"* ]]; then
  echo "entry-scope rehearsal failed for an unexpected reason" >&2
  exit 1
fi

"${mysql_command[@]}" -e \
  "DROP DATABASE ${MIGRATION_DATABASE};
   CREATE DATABASE ${MIGRATION_DATABASE}
     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
"${mysql_command[@]}" "${MIGRATION_DATABASE}" < "${MIGRATION_FIXTURE}"
"${mysql_command[@]}" "${MIGRATION_DATABASE}" -e \
  "INSERT INTO contest (id, status) VALUES (2, 'REVIEWING');
   UPDATE contest_judge SET contest_id = 2 WHERE id = 4000;"

if assignment_scope_error="$("${mysql_command[@]}" "${MIGRATION_DATABASE}" \
  < "${MIGRATION_SCRIPT}" 2>&1)"; then
  echo "review assignment with a cross-contest judge was accepted" >&2
  exit 1
fi
if [[ "${assignment_scope_error}" != *"review assignment judge belongs to another contest"* ]]; then
  echo "assignment-scope rehearsal failed for an unexpected reason" >&2
  exit 1
fi

"${mysql_command[@]}" -e \
  "DROP DATABASE ${MIGRATION_DATABASE};
   CREATE DATABASE ${MIGRATION_DATABASE}
     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
"${mysql_command[@]}" "${MIGRATION_DATABASE}" < "${MIGRATION_FIXTURE}"
"${mysql_command[@]}" "${MIGRATION_DATABASE}" -e \
  "UPDATE contest_stage
   SET status = 'OPEN',
       target_type = 'ALL_SUBMISSIONS',
       pass_rule = 'TOP_N',
       pass_count = 1,
       ends_at = DATE_ADD(NOW(6), INTERVAL 1 DAY);"

if multiple_open_error="$("${mysql_command[@]}" "${MIGRATION_DATABASE}" \
  < "${MIGRATION_SCRIPT}" 2>&1)"; then
  echo "multiple open review stages were accepted" >&2
  exit 1
fi
if [[ "${multiple_open_error}" != *"contest has multiple open review stages"* ]]; then
  echo "multiple-open rehearsal failed for an unexpected reason" >&2
  exit 1
fi

"${mysql_command[@]}" -e \
  "DROP DATABASE ${MIGRATION_DATABASE};
   CREATE DATABASE ${MIGRATION_DATABASE}
     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
"${mysql_command[@]}" "${MIGRATION_DATABASE}" < "${MIGRATION_FIXTURE}"
"${mysql_command[@]}" "${MIGRATION_DATABASE}" -e \
  "UPDATE contest_stage SET status = 'PREPARING' WHERE id = 1000;"

if lifecycle_error="$("${mysql_command[@]}" "${MIGRATION_DATABASE}" \
  < "${MIGRATION_SCRIPT}" 2>&1)"; then
  echo "non-sequential review lifecycle was accepted" >&2
  exit 1
fi
if [[ "${lifecycle_error}" != *"review stage lifecycle is not sequential"* ]]; then
  echo "lifecycle rehearsal failed for an unexpected reason" >&2
  exit 1
fi

"${mysql_command[@]}" -e \
  "DROP DATABASE ${MIGRATION_DATABASE};
   CREATE DATABASE ${MIGRATION_DATABASE}
     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
"${mysql_command[@]}" "${MIGRATION_DATABASE}" < "${MIGRATION_FIXTURE}"
"${mysql_command[@]}" "${MIGRATION_DATABASE}" -e \
  "UPDATE team
   SET participation_finalized_at = NULL
   WHERE id = 900;"

if team_finalization_error="$("${mysql_command[@]}" "${MIGRATION_DATABASE}" \
  < "${MIGRATION_SCRIPT}" 2>&1)"; then
  echo "review entry for an unfinalized team was accepted" >&2
  exit 1
fi
if [[ "${team_finalization_error}" != *"review entry team participation is not finalized"* ]]; then
  echo "team-finalization rehearsal failed for an unexpected reason" >&2
  exit 1
fi

"${mysql_command[@]}" -e \
  "DROP DATABASE ${MIGRATION_DATABASE};
   CREATE DATABASE ${MIGRATION_DATABASE}
     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
"${mysql_command[@]}" "${MIGRATION_DATABASE}" < "${MIGRATION_FIXTURE}"
"${mysql_command[@]}" "${MIGRATION_DATABASE}" -e \
  "UPDATE contest_stage_entry
   SET final_score = 1.00
   WHERE id = 3001;"

if manual_score_error="$("${mysql_command[@]}" "${MIGRATION_DATABASE}" \
  < "${MIGRATION_SCRIPT}" 2>&1)"; then
  echo "manual no-review stage with a score was accepted" >&2
  exit 1
fi
if [[ "${manual_score_error}" != *"manual no-review stage contains a score"* ]]; then
  echo "manual-score rehearsal failed for an unexpected reason" >&2
  exit 1
fi

"${mysql_command[@]}" -e \
  "DROP DATABASE ${MIGRATION_DATABASE};
   CREATE DATABASE ${MIGRATION_DATABASE}
     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
"${mysql_command[@]}" "${MIGRATION_DATABASE}" < "${MIGRATION_FIXTURE}"
"${mysql_command[@]}" "${MIGRATION_DATABASE}" -e \
  "UPDATE contest SET status = 'AWARDED' WHERE id = 1;"

if awarded_evidence_error="$("${mysql_command[@]}" "${MIGRATION_DATABASE}" \
  < "${MIGRATION_SCRIPT}" 2>&1)"; then
  echo "awarded contest without confirmed award evidence was accepted" >&2
  exit 1
fi
if [[ "${awarded_evidence_error}" != *"awarded contest has no confirmed award"* ]]; then
  echo "awarded-evidence rehearsal failed for an unexpected reason" >&2
  exit 1
fi

echo "Review round migration rehearsal passed."
