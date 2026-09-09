#!/bin/bash
# Explicit operator-only offline restore audit. Never imports into an existing container/database.
set -euo pipefail
set +x
export PATH=/usr/sbin:/usr/bin:/sbin:/bin
export LC_ALL=C
unset MYSQL_PWD MYSQL_HOST MYSQL_TCP_PORT MYSQL_UNIX_PORT DOCKER_HOST DOCKER_CONTEXT

# Conservative mysqldump lexer: quoted data is never mistaken for executable SQL; executable
# version comments ARE checked. This accepts table-only dumps, not general SQL migrations.
check_sql() {
  awk -v expected="$1" '
  function fail() { bad=1; exit 1 }
  function finish( s) {
    s=toupper(statement); gsub(/^[[:space:]]+|[[:space:]]+$/, "", s); statement="";
    if (s == "") return;
    if (s ~ /(^|[^A-Z_])(USE|GRANT|REVOKE|OUTFILE|INFILE|LOAD|CALL|HANDLER|PREPARE|EXECUTE|DEALLOCATE|SHUTDOWN|INSTALL|UNINSTALL|DELIMITER)([^A-Z_]|$)/) fail();
    if (s ~ /(^|[^A-Z_])(DATABASE|SCHEMA|TABLESPACE|PROCEDURE|FUNCTION|TRIGGER|EVENT|DEFINER|GLOBAL|PERSIST|SQL_LOG_BIN)([^A-Z_]|$)/) fail();
    if (s !~ /^SET[[:space:]]/ && (s ~ /`[[:space:]]*\./ || s ~ /[A-Z_][A-Z_0-9]*[[:space:]]*\.[[:space:]]*[A-Z_`]/)) fail();
    if (s ~ /^CREATE[[:space:]]+TABLE[[:space:]]/) { creates++; return }
    if (s ~ /^DROP[[:space:]]+TABLE[[:space:]]+IF[[:space:]]+EXISTS[[:space:]]/) return;
    if (s ~ /^INSERT[[:space:]]+INTO[[:space:]]/ && s !~ /(^|[^A-Z_])SELECT([^A-Z_]|$)/) return;
    if (s ~ /^LOCK[[:space:]]+TABLES[[:space:]]/ || s ~ /^UNLOCK[[:space:]]+TABLES$/) return;
    if (s ~ /^ALTER[[:space:]]+TABLE[[:space:]]+`[A-Z_0-9]+`[[:space:]]+(DISABLE|ENABLE)[[:space:]]+KEYS$/) return;
    if (s ~ /^SET[[:space:]]/ || s == "START TRANSACTION" || s == "COMMIT") return;
    fail();
  }
  {
    line=$0 "\n";
    for (i=1; i<=length(line); i++) {
      c=substr(line,i,1); n=substr(line,i+1,1);
      if (comment) { if (c == "*" && n == "/") { comment=0; i++; statement=statement " " } continue }
      if (quote != "") {
        if (escaped) { escaped=0; continue }
        if (c == "\\" && quote != "`") { escaped=1; continue }
        if (c == quote) {
          if (n == quote) { i++; continue }
          if (quote == "`") statement=statement "`";
          quote="";
        } else if (quote == "`") statement=statement c;
        continue;
      }
      if (version && c == "*" && n == "/") { version=0; i++; statement=statement " "; continue }
      if (c == "#" || (c == "-" && n == "-" && substr(line,i+2,1) ~ /[[:space:]]/)) break;
      if (c == "/" && n == "*") {
        if (substr(line,i+2,1) == "!") { version=1; i+=2; while (substr(line,i+1,1) ~ /[0-9]/) i++ }
        else { comment=1; i++ }
        statement=statement " "; continue;
      }
      if (c == "\047" || c == "\042" || c == "`") { quote=c; statement=statement (c == "`" ? "`" : " "); continue }
      if (c == "\\") fail();
      if (c == ";") finish(); else statement=statement c;
      if (length(statement) > 4194304) fail();
    }
  }
  END { if (bad || quote != "" || comment || version || statement !~ /^[[:space:]]*$/ || creates != expected) exit 1 }
  '
}

if [[ ${1:-} == --self-test && $# == 1 ]]; then
  safe=$'-- synthetic\n/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;\nDROP TABLE IF EXISTS `sample`;\nCREATE TABLE `sample` (`id` bigint, `text` text);\nLOCK TABLES `sample` WRITE;\nINSERT INTO `sample` VALUES (1,\047USE other; CREATE DATABASE malicious;\047);\n/*!40000 ALTER TABLE `sample` ENABLE KEYS */;\nUNLOCK TABLES;\n'
  printf '%s' "$safe" | check_sql 1
  for unsafe in 'USE other;' 'CREATE DATABASE other;' 'GRANT ALL ON *.* TO x;' \
    '/*!50000 USE other */;' 'INSERT INTO `other`.`sample` VALUES (1);' \
    'INSERT INTO other.sample VALUES (1);' 'SET @@GLOBAL.max_connections=9;' \
    'SELECT 1 INTO OUTFILE "/tmp/x";' '\! touch /tmp/x' 'CREATE TRIGGER bad BEFORE INSERT ON x FOR EACH ROW SET @x=1;'; do
    if printf '%s\n%s\n' "$safe" "$unsafe" | check_sql 1; then
      printf '%s\n' 'RESTORE_GUARD_SELF_TEST_FAILED' >&2; exit 1
    fi
  done
  printf '%s\n' 'RESTORE_GUARD_SELF_TEST_PASS cases=11 network=false secrets=false'
  exit 0
fi

[[ $# == 1 && $1 == --run-approved-release-rehearsal ]] || { printf '%s\n' 'RESTORE_EXPLICIT_APPROVAL_REQUIRED' >&2; exit 1; }
[[ $(id -u) == 0 ]] || { printf '%s\n' 'RESTORE_ROOT_REQUIRED' >&2; exit 1; }
umask 077
source_dir=/srv/trekkey-backups/sui-release-20260908-online
dump=$source_dir/database.sql.gz
audit_dir=/srv/trekkey-backups/sui-release-20260908-rehearsal-v2
container=trekkey-sui-release-20260908-rehearsal-v2
volume=trekkey-sui-release-20260908-rehearsal-v2-data
image=mysql:8.4
migration=/opt/trekkey/sui-release/2026-09-08-sui-anchor-context.sql
[[ -f $migration && ! -L $migration && $(stat -c %u:%a "$migration") == 0:600 ]]
for tool in docker gzip awk openssl stat readlink sync timeout; do command -v "$tool" >/dev/null || exit 1; done
for dir in /srv /srv/trekkey-backups "$source_dir"; do
  [[ -d $dir && ! -L $dir && $(readlink -f "$dir") == "$dir" && $(stat -c %u "$dir") == 0 ]] || exit 1
  [[ $((8#$(stat -c %a "$dir") & 8#022)) == 0 ]] || exit 1
done
[[ $(stat -c %a "$source_dir") == 700 && -f $dump && ! -L $dump \
  && $(stat -c %u:%a:%h "$dump") == 0:600:1 && $(readlink -f "$dump") == "$dump" ]] || exit 1
[[ ! -e $audit_dir && ! -L $audit_dir ]] || { printf '%s\n' 'RESTORE_EXISTING_AUDIT_REFUSED' >&2; exit 1; }
# Inspect against the local daemon only, not user-configured remote Docker contexts.
docker=(docker --host unix:///var/run/docker.sock)
"${docker[@]}" info >/dev/null 2>&1
"${docker[@]}" image inspect "$image" >/dev/null 2>&1 # --pull=never below: no external network writes/downloads.
if "${docker[@]}" container inspect "$container" >/dev/null 2>&1; then exit 1; fi
if "${docker[@]}" volume inspect "$volume" >/dev/null 2>&1; then exit 1; fi
# Exclusive directory/claim lock serializes cooperating operators; no removal or automatic retry.
mkdir -m 700 "$audit_dir"
stage=INPUT_GUARD
owned_container=false
cleanup() {
  rc=$?
  trap - EXIT INT TERM
  if [[ $owned_container == true ]]; then
    if ! timeout 35 "${docker[@]}" stop --time 20 "$container" >"$audit_dir/stop.stdout" 2>"$audit_dir/stop.stderr"; then rc=1; stage=STOP_FAILED; fi
  fi
  if (( rc != 0 )); then
    (set -C; printf 'RESTORE_AUDIT_FAILED stage=%s data_preserved=true\n' "$stage" >"$audit_dir/failure.txt") || true
    printf 'RESTORE_AUDIT_FAILED stage=%s state_preserved=true\n' "$stage" >&2
  fi
  exit "$rc"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
(set -C; printf '%s\n' 'Fresh offline backup restore claim. Preserve this directory, container and volume after any failure.' >"$audit_dir/run.claim")
sync -f "$audit_dir/run.claim"; sync -f "$audit_dir"
timeout 30 gzip -t "$dump" 2>"$audit_dir/gzip.stderr"
# Values never leave this pipe. Any unknown/unsafe dump grammar stops before container creation.
timeout 30 gzip -dc "$dump" 2>"$audit_dir/decompress.stderr" | check_sql 48
stage=PRIVATE_PASSWORD
(set -C; openssl rand -hex 32 >"$audit_dir/mysql-root-password")
chown 999:999 "$audit_dir/mysql-root-password"; chmod 600 "$audit_dir/mysql-root-password"
sync -f "$audit_dir/mysql-root-password"; sync -f "$audit_dir"
stage=CREATE_VOLUME
# Docker volume create is idempotent, so reject any concurrent existing name again before creation.
# The exclusive root-only audit claim is the operator lock; privileged noncooperating Docker writes are outside this contract.
if "${docker[@]}" volume inspect "$volume" >/dev/null 2>&1; then exit 1; fi
"${docker[@]}" volume create --label trekkey.restore-audit=20260908 "$volume" >"$audit_dir/volume.stdout" 2>"$audit_dir/volume.stderr"
stage=CREATE_CONTAINER
"${docker[@]}" create --name "$container" --pull=never --network none --memory 1g --memory-swap 1g --cpus 1 \
  --pids-limit 256 --restart no --read-only --security-opt no-new-privileges:true \
  --tmpfs /tmp:rw,noexec,nosuid,size=64m --tmpfs /var/run/mysqld:rw,nosuid,size=16m \
  --log-opt max-size=2m --log-opt max-file=1 \
  --mount "type=volume,src=$volume,dst=/var/lib/mysql" \
  --mount "type=bind,src=$dump,dst=/backup/database.sql.gz,readonly" \
  --mount "type=bind,src=$migration,dst=/backup/migration.sql,readonly" \
  --mount "type=bind,src=$audit_dir/mysql-root-password,dst=/run/secrets/mysql-root-password,readonly" \
  --env MYSQL_ROOT_PASSWORD_FILE=/run/secrets/mysql-root-password --env MYSQL_ROOT_HOST=localhost \
  --env MYSQL_DATABASE=trekkey_restore --env MYSQL_USER=restore_import \
  --env MYSQL_PASSWORD_FILE=/run/secrets/mysql-root-password --env TZ=UTC \
  "$image" --skip-networking --local-infile=0 --secure-file-priv=NULL --event-scheduler=OFF \
  --innodb-buffer-pool-size=256M --max-connections=20 \
  >"$audit_dir/container.stdout" 2>"$audit_dir/container.stderr"
owned_container=true
[[ $("${docker[@]}" inspect -f '{{.HostConfig.NetworkMode}}|{{len .HostConfig.PortBindings}}|{{len .Mounts}}' "$container") == 'none|0|4' ]]
stage=START_CONTAINER
"${docker[@]}" start "$container" >"$audit_dir/start.stdout" 2>"$audit_dir/start.stderr"
stage=MYSQL_READY
ready=false
# At most 18 * (2-second probe + 1-second pause), i.e. less than 60 seconds.
for ((attempt=0; attempt<18; attempt++)); do
  if timeout 2 "${docker[@]}" exec --user 0 "$container" sh -c \
    'test "$(cat /proc/1/comm)" = mysqld && MYSQL_PWD="$(cat /run/secrets/mysql-root-password)" exec mysql --no-defaults --protocol=socket --user=restore_import --batch --skip-column-names trekkey_restore -e "SELECT 1"' \
    >/dev/null 2>"$audit_dir/readiness.stderr"; then ready=true; break; fi
  sleep 1
done
[[ $ready == true ]]
stage=FRESH_SCHEMA
fresh=$(timeout 10 "${docker[@]}" exec --user 0 "$container" sh -c \
  'MYSQL_PWD="$(cat /run/secrets/mysql-root-password)" exec mysql --no-defaults --protocol=socket --user=restore_import --batch --skip-column-names trekkey_restore -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()"' \
  2>"$audit_dir/fresh-schema.stderr")
[[ $fresh == 0 ]]
stage=IMPORT
# No client-side commands/local infile, no --force. The table-only importer owns only this fresh schema.
# Both commands are inside the no-network container. Root/password/SQL values never enter host argv/stdout.
timeout 180 "${docker[@]}" exec --user 0 "$container" bash -o pipefail -c \
  'export MYSQL_PWD="$(cat /run/secrets/mysql-root-password)"; gzip -dc /backup/database.sql.gz | mysql --no-defaults --protocol=socket --user=restore_import --binary-mode=1 --local-infile=0 --batch trekkey_restore' \
  >"$audit_dir/import.stdout" 2>"$audit_dir/import.stderr"
stage=COUNTS
counts=$(timeout 15 "${docker[@]}" exec --user 0 "$container" sh -c \
  'MYSQL_PWD="$(cat /run/secrets/mysql-root-password)" exec mysql --no-defaults --protocol=socket --user=restore_import --batch --skip-column-names trekkey_restore -e "SELECT (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()),(SELECT COUNT(*) FROM anc_credential WHERE status=\"ANCHORED\"),(SELECT COUNT(*) FROM anc_credential WHERE status=\"ANCHORED\" AND credential_type=\"AWARD\"),(SELECT COUNT(*) FROM anc_batch),(SELECT COUNT(*) FROM anc_chain_transaction),(SELECT COUNT(*) FROM anc_issuer_key)"' \
  2>"$audit_dir/counts.stderr")
[[ $counts == $'48\t6\t6\t1\t1\t1' ]]

stage=MIGRATION_PREFLIGHT
mysql_restore() {
  timeout 30 "${docker[@]}" exec -i --user 0 "$container" sh -c 'MYSQL_PWD="$(cat /run/secrets/mysql-root-password)" exec mysql --no-defaults --protocol=socket --user=restore_import --binary-mode=1 --local-infile=0 --batch --skip-column-names trekkey_restore'
}
preflight=$(mysql_restore <<'SQL'
SELECT (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND column_name='chain_context' AND table_name IN ('anc_batch','anc_issuer_key','anc_chain_transaction','anc_credential_status_event')),
(SELECT COUNT(*) FROM (SELECT batch_id FROM anc_chain_transaction WHERE operation_type='ANCHOR_BATCH' GROUP BY batch_id HAVING COUNT(*)>1) x),
(SELECT COUNT(*) FROM (SELECT credential_status_event_id FROM anc_chain_transaction WHERE credential_status_event_id IS NOT NULL GROUP BY credential_status_event_id HAVING COUNT(*)>1) y),
(SELECT COUNT(*) FROM anc_chain_transaction WHERE chain_id<>1001 OR OCTET_LENGTH(contract_address)<>20 OR (relayer_address IS NOT NULL AND OCTET_LENGTH(relayer_address)<>20));
SQL
)
[[ $preflight == $'0\t0\t0\t0' ]]
immutable_hash() {
  mysql_restore <<'SQL' | sha256sum | cut -d' ' -f1
SELECT id,SHA2(canonical_bytes,256),SHA2(file_manifest_canonical_bytes,256),SHA2(payload_json,256),HEX(content_hash),HEX(file_manifest_hash),HEX(credential_id_hash),status FROM anc_credential ORDER BY id;
SELECT id,public_id,HEX(batch_id_hash),HEX(merkle_root),HEX(schema_version_hash),HEX(approval_digest),HEX(issuer_signature),SHA2(approval_payload_json,256),approval_nonce,status FROM anc_batch ORDER BY id;
SELECT id,organization_id,key_version,HEX(signer_address),SHA2(signer_ref,256),status,valid_from,valid_until,compromised_at FROM anc_issuer_key ORDER BY id;
SELECT id,chain_id,HEX(contract_address),HEX(relayer_address),HEX(tx_hash),SHA2(signed_raw_transaction,256),tx_nonce,contract_version,status FROM anc_chain_transaction ORDER BY id;
SQL
}
before=$(immutable_hash)
printf '%s\n' "$before" > "$audit_dir/immutable-before.sha256"
stage=MIGRATION
"${docker[@]}" exec --user 0 "$container" sh -c 'MYSQL_PWD="$(cat /run/secrets/mysql-root-password)" exec mysql --no-defaults --protocol=socket --user=restore_import --binary-mode=1 --local-infile=0 --batch trekkey_restore < /backup/migration.sql' >"$audit_dir/migration.stdout" 2>"$audit_dir/migration.stderr"
after=$(immutable_hash)
[[ $before == "$after" && $before =~ ^[0-9a-f]{64}$ ]]
postflight=$(mysql_restore <<'SQL'
SELECT (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND column_name='chain_context' AND column_type='varchar(384)' AND is_nullable='YES' AND table_name IN ('anc_batch','anc_issuer_key','anc_chain_transaction','anc_credential_status_event')),
(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='anc_chain_transaction' AND column_name IN ('contract_address','relayer_address') AND column_type='varbinary(32)'),
(SELECT COUNT(*) FROM anc_chain_transaction WHERE chain_context=CONCAT('KAIA|',chain_id,'|0x',LOWER(HEX(contract_address)),'|',contract_version)),
(SELECT COUNT(*) FROM anc_batch b JOIN anc_chain_transaction t ON t.batch_id=b.id AND t.operation_type='ANCHOR_BATCH' WHERE b.chain_context=t.chain_context),
(SELECT COUNT(*) FROM anc_issuer_key WHERE chain_context IS NULL);
SQL
)
[[ $postflight == $'4\t2\t1\t1\t1' ]]
printf '%s\n' "$before" > "$audit_dir/immutable-before.sha256"
printf '%s\n' "$after" > "$audit_dir/immutable-after.sha256"
sha256sum "$migration" > "$audit_dir/migration.sha256"

# The EXIT trap stops without deleting the container/volume. Record PASS only after stop succeeds.
stage=STOP_CONTAINER
timeout 35 "${docker[@]}" stop --time 20 "$container" >"$audit_dir/stop.stdout" 2>"$audit_dir/stop.stderr"
owned_container=false
[[ $("${docker[@]}" inspect -f '{{.State.Running}}' "$container") == false ]]
stage=RESULT
(set -C; printf '%s\n' '{"status":"PASS","network":"none","ports":0,"tables":48,"anchoredCredentials":6,"anchoredAwards":6,"batches":1,"chainTransactions":1,"issuerKeys":1,"containerStopped":true,"volumePreserved":true,"migrationAppliedToRestoreOnly":true,"immutableRecordsPreserved":true,"productionReplaced":false,"piiPrinted":false}' >"$audit_dir/result.json")
sync -f "$audit_dir/result.json"; sync -f "$audit_dir"
printf '%s\n' 'SUI_RELEASE_REHEARSAL_PASS migration=true original_bytes_preserved=true tables=48 anchoredAwards=6 batches=1 transactions=1 issuerKeys=1 stopped=true volume_preserved=true'
