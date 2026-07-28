#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ACCOUNT="${USER:?USER is required}"
SERVICE_PREFIX="io.trekkey.kairos-e2e"
ISSUER_SERVICE="${SERVICE_PREFIX}.issuer"
RELAYER_SERVICE="${SERVICE_PREFIX}.relayer"

usage() {
  printf 'Usage: %s bootstrap|addresses|issuer-proof|run-live-e2e\n' "$0" >&2
  exit 2
}

has_secret() {
  security find-generic-password -a "$ACCOUNT" -s "$1" >/dev/null 2>&1
}

create_secret() {
  local service="$1"
  local private_key

  if has_secret "$service"; then
    return
  fi

  private_key="0x$(openssl rand -hex 32)"
  security add-generic-password \
    -a "$ACCOUNT" \
    -s "$service" \
    -w "$private_key" \
    -U \
    >/dev/null
  unset private_key
}

read_secret() {
  security find-generic-password -a "$ACCOUNT" -s "$1" -w
}

address_for() {
  read_secret "$1" | node "$SCRIPT_DIR/address-from-private-key.mjs"
}

run_live_e2e() {
  local repository_root issuer_key relayer_key

  if [[ "${KAIROS_LIVE_E2E_CONFIRM:-}" != "I_UNDERSTAND_KAIROS_WRITES" ]]; then
    printf 'Set KAIROS_LIVE_E2E_CONFIRM=I_UNDERSTAND_KAIROS_WRITES to run real Kairos writes.\n' >&2
    exit 2
  fi

  repository_root="$(cd "$SCRIPT_DIR/../.." && pwd)"
  (
    cd "$SCRIPT_DIR/.."
    npm run read:kairos --silent
  )

  set +x
  umask 077
  ulimit -c 0 2>/dev/null || true
  unset HISTFILE
  issuer_key="$(read_secret "$ISSUER_SERVICE")"
  relayer_key="$(read_secret "$RELAYER_SERVICE")"
  export KAIROS_E2E_ISSUER_PRIVATE_KEY="$issuer_key"
  export KAIROS_E2E_RELAYER_PRIVATE_KEY="$relayer_key"
  unset issuer_key relayer_key

  cd "$repository_root"
  exec ./gradlew test \
    --no-daemon \
    --tests 'com.api.trekkey.domain.credential.live.KairosCredentialLiveE2ETest' \
    --rerun-tasks
}

case "${1:-}" in
  bootstrap)
    create_secret "$ISSUER_SERVICE"
    create_secret "$RELAYER_SERVICE"
    printf 'issuer=%s\n' "$(address_for "$ISSUER_SERVICE")"
    printf 'relayer=%s\n' "$(address_for "$RELAYER_SERVICE")"
    ;;
  addresses)
    printf 'issuer=%s\n' "$(address_for "$ISSUER_SERVICE")"
    printf 'relayer=%s\n' "$(address_for "$RELAYER_SERVICE")"
    ;;
  issuer-proof)
    read_secret "$ISSUER_SERVICE" | node "$SCRIPT_DIR/sign-issuer-proof-from-stdin.mjs"
    ;;
  run-live-e2e)
    run_live_e2e
    ;;
  *)
    usage
    ;;
esac
