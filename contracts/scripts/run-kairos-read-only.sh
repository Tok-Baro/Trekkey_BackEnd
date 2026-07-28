#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
KEYCHAIN="$SCRIPT_DIR/kairos-e2e-keychain.sh"

if [[ -z "${JAVA_HOME:-}" && -x /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin/java ]]; then
  export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
fi

export KAIROS_READ_ONLY_LIVE=true
export KAIROS_E2E_ORGANIZATION_PUBLIC_ID="${KAIROS_E2E_ORGANIZATION_PUBLIC_ID:-725050e0-2a2f-48a8-a8b8-2e51e12524b7}"
export KAIROS_E2E_ISSUER_KEY_VERSION="${KAIROS_E2E_ISSUER_KEY_VERSION:-2}"
export KAIROS_E2E_ISSUER_SIGNER_ADDRESS="$("$KEYCHAIN" addresses | sed -n 's/^issuer=//p')"
unset KAIROS_E2E_RELAYER_PRIVATE_KEY

(
  cd "$SCRIPT_DIR/.."
  npm run read:kairos --silent
)

cd "$REPOSITORY_ROOT"
exec ./gradlew test \
  --no-daemon \
  --tests 'com.api.trekkey.domain.credential.live.KairosReadOnlyLiveTest' \
  --rerun-tasks
