package com.api.trekkey.domain.credential.integration;

/** Explicit recovery launcher for the reviewed two-confirmed-anchor state; no fresh run fallback. */
public final class SuiPersistentWorkflowResumeRunner {
    private SuiPersistentWorkflowResumeRunner() {}

    public static void main(String[] args) {
        if (args.length != 0 || !"true".equals(System.getenv("SUI_FULL_E2E"))
                || !"two-confirmed-anchors".equals(System.getenv("SUI_FULL_RESUME"))) {
            System.out.println("SUI_FULL_RESULT resume=refused status=NOT_RUN");
            System.exit(2);
            return;
        }
        SuiPersistentWorkflowE2ERunner.main(args);
    }
}
