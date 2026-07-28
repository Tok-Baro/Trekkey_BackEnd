package com.api.trekkey.domain.team.publicapi.web.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ApplicationProgressRes(
        String contestPublicId,
        List<Step> steps
) {
    public record Step(
            StepType type,
            String label,
            StepStatus status,
            String description,
            LocalDateTime occurredAt
    ) {
        public static Step of(
                StepType type,
                StepStatus status,
                String description,
                LocalDateTime occurredAt) {
            return new Step(type, type.label, status, description, occurredAt);
        }
    }

    public enum StepType {
        APPLICATION_RECEIVED("신청 접수"),
        APPLICATION_REVIEW("신청 검토"),
        SUBMISSION("제출물"),
        REVIEW("심사"),
        RESULT("결과");

        private final String label;

        StepType(String label) {
            this.label = label;
        }
    }

    public enum StepStatus {
        COMPLETED,
        IN_PROGRESS,
        WAITING,
        FAILED
    }
}
