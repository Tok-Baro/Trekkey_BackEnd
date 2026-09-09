package com.api.trekkey.domain.credential.integration;

import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * Tests-only standalone launcher for a protected remote Java 21 tool container. Requires the
 * existing testRuntimeClasspath (including junit-platform-launcher); no remote Gradle is needed.
 * Reflection keeps the production/build dependency graph unchanged: launcher is runtime-only.
 * Never prints arbitrary exception messages, stack traces, environment variables or RPC payloads.
 */
public final class SuiPersistentWorkflowE2ERunner {
    private SuiPersistentWorkflowE2ERunner() {}

    public static void main(String[] args) {
        PrintStream report = System.out;
        if (!"true".equals(System.getenv("SUI_FULL_E2E"))) {
            report.println("SUI_FULL_RESULT gate=disabled found=0 executed=0 status=NOT_RUN");
            System.exit(2);
        }
        // Framework context failures can otherwise include property values. Forward only the
        // deliberate public evidence records emitted by our one selected synthetic test.
        System.setOut(new PrintStream(OutputStream.nullOutputStream()) {
            @Override public void println(String line) {
                if (line != null && (line.startsWith("SUI_FULL_PUBLIC_VALID ")
                        || line.startsWith("SUI_FULL_PUBLIC_REVOKED ") || line.startsWith("SUI_FULL_TRANSACTION ")
                        || line.startsWith("SUI_FULL_RESULT "))) {
                    report.println(line);
                }
            }
        });
        System.setErr(new PrintStream(OutputStream.nullOutputStream()));
        int exitCode = 1;
        try {
            Class<?> builderType = Class.forName("org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder");
            Class<?> selectorType = Class.forName("org.junit.platform.engine.DiscoverySelector");
            Class<?> selectorsType = Class.forName("org.junit.platform.engine.discovery.DiscoverySelectors");
            Class<?> requestType = Class.forName("org.junit.platform.launcher.LauncherDiscoveryRequest");
            Class<?> listenerType = Class.forName("org.junit.platform.launcher.TestExecutionListener");
            Class<?> launcherType = Class.forName("org.junit.platform.launcher.Launcher");
            Class<?> summaryType = Class.forName("org.junit.platform.launcher.listeners.TestExecutionSummary");
            Object builder = builderType.getMethod("request").invoke(null);
            Object selector = selectorsType.getMethod("selectClass", String.class).invoke(null,
                    "com.api.trekkey.domain.credential.integration.SuiPersistentWorkflowIntegrationTest");
            Object selectors = Array.newInstance(selectorType, 1);
            Array.set(selectors, 0, selector);
            builderType.getMethod("selectors", selectors.getClass()).invoke(builder, selectors);
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
            long failedContainers = count(summaryType, summary, "getContainersFailedCount");
            boolean passed = found == 1 && succeeded == 1 && failed == 0 && skipped == 0 && aborted == 0 && failedContainers == 0;
            report.println("SUI_FULL_RESULT found=" + found + " succeeded=" + succeeded + " failed=" + failed
                    + " skipped=" + skipped + " aborted=" + aborted + " failedContainers=" + failedContainers
                    + " status=" + (passed ? "PASS" : "FAIL"));
            Class<?> failureType = Class.forName("org.junit.platform.launcher.listeners.TestExecutionSummary$Failure");
            for (Object failure : (List<?>) summaryType.getMethod("getFailures").invoke(summary)) {
                Throwable error = (Throwable) failureType.getMethod("getException").invoke(failure);
                report.println("SUI_FULL_FAILURE type=" + error.getClass().getName());
                // Source locations are safe; failure messages may carry sensitive property values.
                for (StackTraceElement frame : error.getStackTrace()) {
                    if (frame.getClassName().equals(SuiPersistentWorkflowIntegrationTest.class.getName())) {
                        report.println("SUI_FULL_FAILURE location=" + frame.getClassName() + ":" + frame.getLineNumber());
                        break;
                    }
                }
            }
            exitCode = passed ? 0 : 1;
        } catch (Throwable error) {
            if (error instanceof InvocationTargetException wrapped && wrapped.getCause() != null) error = wrapped.getCause();
            report.println("SUI_FULL_RUNNER_ERROR type=" + error.getClass().getName());
        } finally {
            System.setOut(report);
            report.flush();
        }
        // Spring's scheduler/context cache may keep non-daemon threads alive after launcher return.
        System.exit(exitCode);
    }

    private static long count(Class<?> summaryType, Object summary, String method) throws Exception {
        return ((Number) summaryType.getMethod(method).invoke(summary)).longValue();
    }
}
