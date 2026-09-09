#!/bin/sh
set -eu
test "$(id -u)" = 0
root=/srv/trekkey-sui-testnet
bundle=${TESTNET_E2E_BUNDLE_DIR:?explicit clean test bundle required}
case "$bundle" in "$root"/artifacts/full-integration-0615984/v[0-9]*/e2e) ;; *) exit 2;; esac
test -d "$bundle" && test ! -L "$bundle"
infra=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec docker run --rm --network host --user 10001:10001 --read-only \
  --cap-drop ALL --security-opt no-new-privileges --memory 1536m --cpus 1 --pids-limit 256 \
  --tmpfs /tmp:rw,nosuid,size=256m,uid=10001,gid=10001,mode=1770 \
  --mount "type=bind,source=$bundle,target=/app/e2e,readonly" \
  --mount "type=bind,source=$root/secrets/mysql-suite/MYSQL_TEST_PASSWORD,target=/run/mysql-suite/MYSQL_TEST_PASSWORD,readonly" \
  --mount "type=bind,source=$infra/run-mysql-suite.sh,target=/app/run-mysql-suite.sh,readonly" \
  --entrypoint /bin/sh eclipse-temurin:21-jdk-jammy /app/run-mysql-suite.sh
