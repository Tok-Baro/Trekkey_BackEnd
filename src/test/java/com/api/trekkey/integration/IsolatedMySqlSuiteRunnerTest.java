package com.api.trekkey.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IsolatedMySqlSuiteRunnerTest {
    private static final String CLASS = "com.api.trekkey.domain.auth.integration.AuthMySqlIntegrationTest";
    private Map<String, String> environment() {
        return new HashMap<>(Map.of("RUN_MYSQL_INTEGRATION_TESTS", "true",
                "MYSQL_TEST_URL", IsolatedMySqlSuiteRunner.URL, "MYSQL_TEST_USERNAME", IsolatedMySqlSuiteRunner.USER,
                "MYSQL_TEST_PASSWORD", "ab".repeat(32), "BLOCKCHAIN_ANCHORING_MODE", "DISABLED",
                "BLOCKCHAIN_WORKER_ENABLED", "false"));
    }
    @Test void selectedClassAndExactIsolatedDatabaseAreRequired() {
        assertEquals(4, IsolatedMySqlSuiteRunner.validateInvocation(new String[]{CLASS}, environment()));
        assertThrows(IllegalArgumentException.class, () -> IsolatedMySqlSuiteRunner.validateInvocation(new String[]{"arbitrary.Test"}, environment()));
        var env = environment(); env.put("MYSQL_TEST_URL", IsolatedMySqlSuiteRunner.URL.replace("trekkey_test", "trekkey_sui_testnet"));
        assertThrows(IllegalArgumentException.class, () -> IsolatedMySqlSuiteRunner.validateInvocation(new String[]{CLASS}, env));
    }
    @Test void rootDatabaseUserDisabledGateAndRuntimeProfileLeakageAreRefused() {
        for (var change : Map.of("MYSQL_TEST_USERNAME", "root", "RUN_MYSQL_INTEGRATION_TESTS", "false",
                "SPRING_CONFIG_IMPORT", "configtree:/run/secrets/", "SUI_GATEWAY_TOKEN", "must-not-be-inherited").entrySet()) {
            var env = environment(); env.put(change.getKey(), change.getValue());
            assertThrows(IllegalArgumentException.class, () -> IsolatedMySqlSuiteRunner.validateInvocation(new String[]{CLASS}, env));
        }
    }
    @Test void allowlistedCountsMatchAllTwentySixCurrentDatabaseTestsWithoutExecutingThem() throws Exception {
        var classes = List.of(
                CLASS,
                "com.api.trekkey.domain.contest.integration.ContestLegacyStageMySqlIntegrationTest",
                "com.api.trekkey.domain.credential.integration.AncChainTransactionMySqlIntegrationTest",
                "com.api.trekkey.domain.review.integration.ReviewAssignmentMySqlIntegrationTest",
                "com.api.trekkey.domain.review.integration.ReviewFileMySqlIntegrationTest",
                "com.api.trekkey.domain.review.integration.ReviewLifecycleMySqlIntegrationTest",
                "com.api.trekkey.domain.review.integration.ReviewRoundConfigurationMySqlIntegrationTest",
                "com.api.trekkey.domain.review.integration.ReviewRoundEntryMySqlIntegrationTest",
                "com.api.trekkey.domain.review.integration.ReviewSubmissionMySqlIntegrationTest");
        int total = 0;
        for (var className : classes) {
            var type = Class.forName(className, false, getClass().getClassLoader());
            long declaredTests = Arrays.stream(type.getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(Test.class)).count();
            int expected = IsolatedMySqlSuiteRunner.validateInvocation(new String[]{className}, environment());
            assertEquals(declaredTests, expected, className);
            total += expected;
        }
        assertEquals(26, total);
    }
}
