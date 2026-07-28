#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
KEYCHAIN="$SCRIPT_DIR/kairos-e2e-keychain.sh"

if [[ -z "${JAVA_HOME:-}" && -x /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin/java ]]; then
  export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
fi

export KAIROS_LIVE_E2E=true
export KAIROS_E2E_ORGANIZATION_PUBLIC_ID="${KAIROS_E2E_ORGANIZATION_PUBLIC_ID:-725050e0-2a2f-48a8-a8b8-2e51e12524b7}"
export KAIROS_E2E_ISSUER_KEY_VERSION="${KAIROS_E2E_ISSUER_KEY_VERSION:-2}"

cd "$REPOSITORY_ROOT"
exec "$KEYCHAIN" run-live-e2e
