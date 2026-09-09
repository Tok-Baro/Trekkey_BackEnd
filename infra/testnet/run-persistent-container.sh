#!/bin/sh
set -eu
test "$(id -u)" = 0
root=/srv/trekkey-sui-testnet
bundle=${TESTNET_E2E_BUNDLE_DIR:?explicit clean test bundle required}
case "$bundle" in "$root"/artifacts/full-integration-0615984/v[0-9]*/e2e) ;; *) exit 2;; esac
test -d "$bundle" && test ! -L "$bundle"
case "$#:${1:-}" in
  0:)
    test ! -e "$root/full-e2e/run.claim"
    runner=com.api.trekkey.domain.credential.integration.SuiPersistentWorkflowE2ERunner
    ;;
  1:--resume-two-confirmed-anchors)
    test -f "$root/full-e2e/run.claim"
    test ! -e "$root/full-e2e/resume-two-anchors.claim"
    runner=com.api.trekkey.domain.credential.integration.SuiPersistentWorkflowResumeRunner
    set -- -e SUI_FULL_RESUME=two-confirmed-anchors
    ;;
  *) exit 2;;
esac
test "$(docker exec trekkey-sui-testnet-gateway-1 node -e 'process.stdout.write(process.env.SUI_GAS_BUDGET??"50000000")')" = 50000000
exec docker run --rm --network host --user 10001:10001 --read-only \
  --cap-drop ALL --security-opt no-new-privileges --memory 1536m --cpus 1.5 --pids-limit 256 \
  --tmpfs /tmp:rw,nosuid,size=256m,uid=10001,gid=10001,mode=1770 \
  --tmpfs /state:rw,noexec,nosuid,size=1m,uid=10001,gid=10001,mode=0700 \
  --mount "type=bind,source=$bundle,target=/app/e2e,readonly" \
  --mount "type=bind,source=$root/secrets/deployment/deployment.json,target=/state/deployment.json,readonly" \
  --mount "type=bind,source=$root/secrets/deployment/synthetic-issuer.key,target=/state/synthetic-issuer.key,readonly" \
  --mount "type=bind,source=$root/secrets/full/DATASOURCE_PASSWORD,target=/run/DATASOURCE_PASSWORD,readonly" \
  --mount "type=bind,source=$root/secrets/full/E2E_LOGIN_PASSWORD,target=/run/E2E_LOGIN_PASSWORD,readonly" \
  --mount "type=bind,source=$root/secrets/gateway/gateway-token,target=/run/gateway-token,readonly" \
  --mount "type=bind,source=$root/full-e2e,target=/run-e2e" \
  --mount "type=bind,source=$root/uploads-full,target=/uploads" \
  -e SUI_FULL_E2E=true -e SUI_FULL_DB_PASSWORD_FILE=/run/DATASOURCE_PASSWORD \
  -e SUI_FULL_LOGIN_PASSWORD_FILE=/run/E2E_LOGIN_PASSWORD -e SUI_FULL_RUN_DIR=/run-e2e \
  -e SUI_FULL_UPLOAD_DIR=/uploads -e SUI_FULL_GAS_BUDGET_MIST=50000000 \
  -e SUI_E2E_STATE_DIR=/state -e SUI_GATEWAY_TOKEN_FILE=/run/gateway-token \
  -e SUI_E2E_GATEWAY_URL=http://127.0.0.1:9187 \
  "$@" \
  --entrypoint java eclipse-temurin:21-jdk-jammy -Xmx1024m -XX:ActiveProcessorCount=2 \
  -cp '/app/e2e/main:/app/e2e/test:/app/e2e/lib/*' \
  "$runner"
