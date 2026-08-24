package com.api.trekkey.domain.evidence.entity;

public final class EvidenceTypes {
    private EvidenceTypes() {}

    public enum EvidenceType {
        QUALIFICATION, LANGUAGE_SCORE, TOPIK_SCORE, ENGLISH_SCORE,
        CONTEST_PARTICIPATION, CONTEST_AWARD, INDUSTRY_PROJECT, CAPSTONE,
        COMPLETION, ENROLLMENT, EMPLOYMENT, THESIS, GRADUATION_WORK,
        GRADUATION_EXAM, RESEARCH_PLAN, OTHER
    }

    public enum EvidenceStatus {
        UNDER_REVIEW, AWAITING_SECOND_REVIEW, VERIFIED, REJECTED, INCONCLUSIVE
    }

    public enum FileSafetyStatus { FORMAT_VALIDATED }
    public enum VerificationCaseStatus {
        MANUAL_REVIEW, AWAITING_SECOND_REVIEW, VERIFIED, REJECTED, INCONCLUSIVE
    }
    public enum ReviewResult { APPROVE, REJECT }
    public enum ReviewReasonCode {
        OFFICIAL_SOURCE_MATCH, OFFICIAL_SOURCE_MISMATCH, SUBJECT_MISMATCH,
        EXPIRED_DOCUMENT, INSUFFICIENT_EVIDENCE
    }
    public enum VerificationDecisionType { VERIFIED, REJECTED, INCONCLUSIVE }
    public enum AssuranceLevel { L0, L1, L2, L3, L4 }
}
