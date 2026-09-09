#!/bin/bash
# Fixed-host, server-local preservation. Never prints secret values or exports backups.
set -euo pipefail
set +x
export PATH=/usr/sbin:/usr/bin:/sbin:/bin LC_ALL=C
unset DOCKER_HOST DOCKER_CONTEXT MYSQL_PWD MYSQL_HOST MYSQL_TCP_PORT
[[ $(id -u) == 0 && $# == 1 ]]
case "$1" in --online) phase=online;; --cutover) phase=cutover;; *) exit 2;; esac
umask 077
docker=(docker --host unix:///var/run/docker.sock)
base=/srv/trekkey-backups
backup=$base/sui-release-20260908-$phase
[[ -d $base && ! -L $base && $(readlink -f "$base") == "$base" && $(stat -c %u:%a "$base") == 0:700 ]]
[[ ! -e $backup && ! -L $backup ]]
[[ $("${docker[@]}" inspect -f '{{.Config.Image}}' trekkey-backend-1) == ghcr.io/tok-baro/trekkey_backend:0615984797b7c1c6b51f2f9e7ae7ca41332ae15c ]]
[[ $("${docker[@]}" inspect -f '{{.Config.Image}}' trekkey-mysql-1) == mysql:8.4 ]]
[[ $("${docker[@]}" inspect -f '{{.Config.Image}}' trekkey-sui-testnet-mysql-1) == mysql:8.4 ]]
if [[ $phase == cutover ]]; then
  for name in trekkey-backend-1 trekkey-sui-testnet-backend-1 trekkey-sui-testnet-gateway-1; do
    [[ $("${docker[@]}" inspect -f '{{.State.Running}}' "$name") == false ]]
  done
fi
mkdir -m 700 "$backup"
printf 'phase=%s\nserver_local=true\nrestore_before_cutover_required=true\n' "$phase" > "$backup/backup.claim"
stage=DATABASES
trap 'rc=$?; if ((rc)); then printf "BACKUP_FAILED stage=%s partial_files_preserved=true\n" "$stage" >&2; fi' EXIT
dump_database() {
  local container=$1 database=$2 output=$3
  "${docker[@]}" exec "$container" sh -c '
    set -eu
    case "$1" in trekkey|trekkey_sui_testnet|trekkey_sui_full) ;; *) exit 2;; esac
    if test -n "${MYSQL_ROOT_PASSWORD_FILE:-}"; then
      IFS= read -r MYSQL_PWD < "$MYSQL_ROOT_PASSWORD_FILE" || test -n "$MYSQL_PWD"
    else MYSQL_PWD=$MYSQL_ROOT_PASSWORD; fi
    export MYSQL_PWD
    exec mysqldump --protocol=socket -uroot --single-transaction --quick --routines --events --triggers --hex-blob --set-gtid-purged=OFF --no-tablespaces "$1"
  ' sh "$database" | gzip > "$backup/$output.sql.gz"
  gzip -t "$backup/$output.sql.gz"
}
dump_database trekkey-mysql-1 trekkey database
dump_database trekkey-sui-testnet-mysql-1 trekkey_sui_testnet testnet-initial
dump_database trekkey-sui-testnet-mysql-1 trekkey_sui_full testnet-full
stage=CONFIGURATION_UPLOADS_AND_TESTNET_STATE
tar -czf "$backup/configuration.tar.gz" -C / opt/trekkey etc/trekkey
tar -czf "$backup/uploads.tar.gz" -C /var/lib/trekkey uploads
# Includes protected testnet keys, journal, uploads, artifacts and execution history, on-server only.
tar -czf "$backup/testnet-state.tar.gz" -C /srv trekkey-sui-testnet
stage=IMAGES
mapfile -t images < <("${docker[@]}" inspect -f '{{.Image}}' trekkey-backend-1 trekkey-mysql-1 trekkey-sui-testnet-backend-1 trekkey-sui-testnet-gateway-1 | sort -u)
[[ ${#images[@]} == 4 ]]
"${docker[@]}" image save "${images[@]}" | gzip > "$backup/service-images.tar.gz"
"${docker[@]}" inspect -f '{{.Name}} {{.Id}} {{.Image}} {{.Config.Image}} {{.State.Running}}' \
  trekkey-backend-1 trekkey-mysql-1 trekkey-sui-testnet-backend-1 trekkey-sui-testnet-mysql-1 trekkey-sui-testnet-gateway-1 > "$backup/container-identity.txt"
stage=VERIFY
for name in database.sql.gz testnet-initial.sql.gz testnet-full.sql.gz configuration.tar.gz uploads.tar.gz testnet-state.tar.gz service-images.tar.gz; do
  gzip -t "$backup/$name"
  [[ $(stat -c %u:%a:%h "$backup/$name") == 0:600:1 ]]
  sha256sum "$backup/$name" >> "$backup/SHA256SUMS"
done
sync -f "$backup/SHA256SUMS"
sync -f "$backup"
printf 'BACKUP_PASS phase=%s path=%s artifacts=7 secrets_printed=false\n' "$phase" "$backup"
sha256sum "$backup/SHA256SUMS"
