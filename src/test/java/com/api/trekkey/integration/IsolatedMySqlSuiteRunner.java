package com.api.trekkey.integration;

import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;

/** Test-only launcher. One allowlisted class per JVM; never scans/executes arbitrary tests. */
public final class IsolatedMySqlSuiteRunner {
    public static final String URL = "jdbc:mysql://127.0.0.1:13306/trekkey_test"
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    public static final String USER = "trekkey_it_suite";
    private static final Map<String, Integer> EXPECTED = Map.of(
            "com.api.trekkey.domain.auth.integration.AuthMySqlIntegrationTest", 4,
            "com.api.trekkey.domain.contest.integration.ContestLegacyStageMySqlIntegrationTest", 1,
            "com.api.trekkey.domain.credential.integration.AncChainTransactionMySqlIntegrationTest", 1,
            "com.api.trekkey.domain.review.integration.ReviewAssignmentMySqlIntegrationTest", 4,
            "com.api.trekkey.domain.review.integration.ReviewFileMySqlIntegrationTest", 2,
            "com.api.trekkey.domain.review.integration.ReviewLifecycleMySqlIntegrationTest", 3,
            "com.api.trekkey.domain.review.integration.ReviewRoundConfigurationMySqlIntegrationTest", 3,
            "com.api.trekkey.domain.review.integration.ReviewRoundEntryMySqlIntegrationTest", 4,
            "com.api.trekkey.domain.review.integration.ReviewSubmissionMySqlIntegrationTest", 4);

    private IsolatedMySqlSuiteRunner() {}

    static int validateInvocation(String[] args, Map<String, String> env) {
        if (args.length != 1 || !EXPECTED.containsKey(args[0])
                || !"true".equals(env.get("RUN_MYSQL_INTEGRATION_TESTS"))
                || !URL.equals(env.get("MYSQL_TEST_URL")) || !USER.equals(env.get("MYSQL_TEST_USERNAME"))
                || !env.getOrDefault("MYSQL_TEST_PASSWORD", "").matches("[0-9a-f]{64}")
                || !"DISABLED".equals(env.get("BLOCKCHAIN_ANCHORING_MODE"))
                || !"false".equals(env.get("BLOCKCHAIN_WORKER_ENABLED"))
                || env.keySet().stream().anyMatch(key -> key.startsWith("SUI_")
                    || key.equals("SPRING_PROFILES_ACTIVE") || key.equals("SPRING_CONFIG_IMPORT")
                    || key.equals("JAVA_TOOL_OPTIONS") || key.equals("JDK_JAVA_OPTIONS"))) {
            throw new IllegalArgumentException("MYSQL_SUITE_UNSAFE_INVOCATION");
        }
        return EXPECTED.get(args[0]);
    }

    private static void verifyDatabase(Map<String, String> env) throws Exception {
        try (var connection = DriverManager.getConnection(URL, USER, env.get("MYSQL_TEST_PASSWORD"));
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT DATABASE(), CURRENT_USER()")) {
            if (!result.next() || !"trekkey_test".equals(result.getString(1))
                    || !(USER + "@%").equals(result.getString(2))) {
                throw new IllegalStateException("MYSQL_SUITE_DATABASE_IDENTITY_MISMATCH");
            }
        }
    }

    public static void main(String[] args) {
        PrintStream report = System.out;
        // Hibernate/JDBC exceptions and assertion messages can carry property values or tokens.
        // Suppress framework output; emit only safe counters, class names and source locations.
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));
        System.setErr(new PrintStream(OutputStream.nullOutputStream()));
        int exitCode = 1;
        try {
            int expected = validateInvocation(args, System.getenv());
            verifyDatabase(System.getenv());
            String selectedClass = args[0];
            Class<?> builderType = Class.forName("org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder");
            Class<?> selectorType = Class.forName("org.junit.platform.engine.DiscoverySelector");
            Class<?> selectorsType = Class.forName("org.junit.platform.engine.discovery.DiscoverySelectors");
            Class<?> requestType = Class.forName("org.junit.platform.launcher.LauncherDiscoveryRequest");
            Class<?> listenerType = Class.forName("org.junit.platform.launcher.TestExecutionListener");
            Class<?> launcherType = Class.forName("org.junit.platform.launcher.Launcher");
            Class<?> summaryType = Class.forName("org.junit.platform.launcher.listeners.TestExecutionSummary");
            Object builder = builderType.getMethod("request").invoke(null);
            Object selector = selectorsType.getMethod("selectClass", String.class).invoke(null, selectedClass);
            Object selectors = Array.newInstance(selectorType, 1);
            Array.set(selectors, 0, selector);
            builderType.getMethod("selectors", selectors.getClass()).invoke(builder, selectors);
            builderType.getMethod("configurationParameter", String.class, String.class)
                    .invoke(builder, "junit.jupiter.execution.parallel.enabled", "false");
            Object request = builderType.getMethod("build").invoke(builder);
            Object listener = Class.forName("org.junit.platform.launcher.listeners.SummaryGeneratingListener")
                    .getConstructor().newInstance();
            Object listeners = Array.newInstance(listenerType, 1);
            Array.set(listeners, 0, listener);
            Object launcher = Class.forName("org.junit.platform.launcher.core.LauncherFactory").getMethod("create").invoke(null);
            launcherType.getMethod("execute", requestType, listeners.getClass()).invoke(launcher, request, listeners);
            Object summary = listener.getClass().getMethod("getSummary").invoke(listener);
            long found = count(summaryType, summary, "getTestsFoundCount");
            long succeeded = count(summaryType, summary, "getTestsSucceededCount");
            long failed = count(summaryType, summary, "getTestsFailedCount");
            long skipped = count(summaryType, summary, "getTestsSkippedCount");
            long aborted = count(summaryType, summary, "getTestsAbortedCount");
            long containersFailed = count(summaryType, summary, "getContainersFailedCount");
            boolean passed = found == expected && succeeded == expected && failed == 0 && skipped == 0
                    && aborted == 0 && containersFailed == 0;
            report.println("MYSQL_SUITE_CLASS class=" + selectedClass + " expected=" + expected + " found=" + found
                    + " succeeded=" + succeeded + " failed=" + failed + " skipped=" + skipped + " aborted=" + aborted
                    + " failedContainers=" + containersFailed + " status=" + (passed ? "PASS" : "FAIL"));
            Class<?> failureType = Class.forName("org.junit.platform.launcher.listeners.TestExecutionSummary$Failure");
            for (Object failure : (List<?>) summaryType.getMethod("getFailures").invoke(summary)) {
                Throwable error = (Throwable) failureType.getMethod("getException").invoke(failure);
                safeFailure(report, "MYSQL_SUITE_FAILURE", error);
            }
            exitCode = passed ? 0 : 1;
        } catch (Throwable error) {
            if (error instanceof InvocationTargetException wrapped && wrapped.getCause() != null) error = wrapped.getCause();
            safeFailure(report, "MYSQL_SUITE_REFUSED", error);
        } finally { report.flush(); }
        // Keep framework shutdown output suppressed. Each class's create-drop closes in this JVM only.
        System.exit(exitCode);
    }

    private static void safeFailure(PrintStream report, String code, Throwable error) {
        int depth = 0;
        for (Throwable cause = error; cause != null && depth < 8; cause = cause.getCause(), depth++) {
            report.println(code + " causeDepth=" + depth + " type=" + cause.getClass().getName());
            int reported = 0;
            for (StackTraceElement frame : cause.getStackTrace()) {
                if (frame.getClassName().startsWith("com.api.trekkey.") || reported == 0) {
                    report.println(code + " location=" + frame.getClassName() + ":" + frame.getLineNumber());
                    if (++reported == 2) break;
                }
            }
        }
    }

    private static long count(Class<?> summaryType, Object summary, String method) throws Exception {
        return ((Number) summaryType.getMethod(method).invoke(summary)).longValue();
    }
}
