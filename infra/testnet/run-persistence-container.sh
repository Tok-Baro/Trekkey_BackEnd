#!/bin/sh
set -eu
test "$(id -u)" = 0
root=/srv/trekkey-sui-testnet
test "$#" = 0
test -f "$root/full-e2e/result.json"
test ! -e "$root/full-e2e/restart-readback.claim"
exec docker run --rm --network host --read-only --user 0:0 \
  --cap-drop ALL --cap-add CHOWN --cap-add FOWNER --cap-add DAC_OVERRIDE --security-opt no-new-privileges \
  --memory 256m --cpus 0.5 --pids-limit 64 \
  --tmpfs /tmp:rw,noexec,nosuid,size=16m,mode=1777 \
  --tmpfs "$root:rw,noexec,nosuid,size=1m,mode=0755" \
  --tmpfs "$root/secrets:rw,noexec,nosuid,size=1m,mode=0700" \
  --tmpfs "$root/secrets/full:rw,noexec,nosuid,size=1m,mode=0700" \
  --mount "type=bind,source=$root/source/full-integration-0615984/infra/testnet,target=/app/infra,readonly" \
  --mount "type=bind,source=$root/full-e2e,target=$root/full-e2e" \
  --mount "type=bind,source=$root/secrets/full/E2E_LOGIN_PASSWORD,target=$root/secrets/full/E2E_LOGIN_PASSWORD,readonly" \
  --entrypoint node node:22.23.2-bookworm-slim \
  /app/infra/verify-full-persistence.mjs --verify-after-restart
