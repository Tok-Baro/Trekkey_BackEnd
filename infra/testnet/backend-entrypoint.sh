#!/bin/sh
set -eu
# Never enable xtrace: Spring reads values from this private config tree itself.
for name in DATASOURCE_PASSWORD JWT_SECRET_KEY SUI_GATEWAY_TOKEN EVIDENCE_LOOKUP_HMAC_SECRET; do
  file="/run/backend-secrets/$name"
  test -f "$file" && test ! -L "$file"
  test "$(stat -c %u "$file")" = "$(id -u)"
  test "$(stat -c %a "$file")" = 600
done
if [ "${TREKKEY_BOOTSTRAP:-false}" = true ]; then
  test "${SUI_TESTNET_BOOTSTRAP_EMPTY_SCHEMA:-}" = I_VERIFIED_NEW_EMPTY_TESTNET_SCHEMA
  # This profile is never enabled by normal `up`. Stop after successful startup.
  exec java -XX:MaxRAMPercentage=65 -jar /app/app.jar \
    --spring.jpa.hibernate.ddl-auto=update --server.port=18081
fi
exec java -XX:MaxRAMPercentage=65 -jar /app/app.jar \
  --spring.jpa.hibernate.ddl-auto=validate --server.port=18080
