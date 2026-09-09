#!/bin/sh
# Run in a disposable Java 21 tool container, not inside the application container.
# The empty environment is established BEFORE reading any private file.
set -eu
exec env -i PATH=/opt/java/openjdk/bin:/usr/bin:/bin /bin/sh -s <<'SUITE'
set -eu
umask 077
test "$(id -u)" = 10001
secret=/run/mysql-suite/MYSQL_TEST_PASSWORD
test -f "$secret" && test ! -L "$secret"
test "$(stat -c %u "$secret")" = 10001
test "$(stat -c %a "$secret")" = 600
test "$(stat -c %h "$secret")" = 1
test "$(stat -c %s "$secret")" = 64
IFS= read -r MYSQL_TEST_PASSWORD < "$secret" || test -n "${MYSQL_TEST_PASSWORD:-}"
test "${#MYSQL_TEST_PASSWORD}" = 64
case "$MYSQL_TEST_PASSWORD" in *[!0-9a-f]*) exit 2;; esac
export MYSQL_TEST_PASSWORD
export RUN_MYSQL_INTEGRATION_TESTS=true
export MYSQL_TEST_URL='jdbc:mysql://127.0.0.1:13306/trekkey_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
export MYSQL_TEST_USERNAME=trekkey_it_suite
export BLOCKCHAIN_ANCHORING_MODE=DISABLED BLOCKCHAIN_WORKER_ENABLED=false
# Public synthetic fixture, not an application authentication secret.
export JWT_SECRET_KEY=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWYwMTIzNDU2Nzg5YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZg==
export JWT_ACCESS_EXPIRATION=1800 JWT_REFRESH_EXPIRATION=1209600
export EVIDENCE_LOOKUP_HMAC_SECRET=synthetic-mysql-suite-evidence-secret-not-for-real-data
export FRONT_BASE_URL=http://localhost:3000 CORS_ALLOWED_ORIGIN=http://localhost:3000
export FILE_UPLOAD_DIR=/tmp/trekkey-mysql-suite-uploads
mkdir "$FILE_UPLOAD_DIR"
for test_class in \
  com.api.trekkey.domain.auth.integration.AuthMySqlIntegrationTest \
  com.api.trekkey.domain.contest.integration.ContestLegacyStageMySqlIntegrationTest \
  com.api.trekkey.domain.credential.integration.AncChainTransactionMySqlIntegrationTest \
  com.api.trekkey.domain.review.integration.ReviewAssignmentMySqlIntegrationTest \
  com.api.trekkey.domain.review.integration.ReviewFileMySqlIntegrationTest \
  com.api.trekkey.domain.review.integration.ReviewLifecycleMySqlIntegrationTest \
  com.api.trekkey.domain.review.integration.ReviewRoundConfigurationMySqlIntegrationTest \
  com.api.trekkey.domain.review.integration.ReviewRoundEntryMySqlIntegrationTest \
  com.api.trekkey.domain.review.integration.ReviewSubmissionMySqlIntegrationTest
do
  java -Xmx1024m -XX:ActiveProcessorCount=2 -Djava.awt.headless=true \
    -cp '/app/e2e/main:/app/e2e/test:/app/e2e/lib/*' \
    com.api.trekkey.integration.IsolatedMySqlSuiteRunner "$test_class" </dev/null
done
printf '%s\n' 'MYSQL_SUITE_RESULT classes=9 expected=26 succeeded=26 status=PASS'
SUITE
