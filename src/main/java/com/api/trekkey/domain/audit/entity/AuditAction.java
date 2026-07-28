package com.api.trekkey.domain.audit.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AuditAction {
    CONTEST_CREATE("contest.create"),
    CONTEST_UPDATE("contest.update"),
    STAGE_STATUS_CHANGE("stage.status_change"),
    CONTEST_JUDGE_CREATE("contest_judge.create"),
    REVIEW_LINK_ISSUE("review_link.issue"),
    REVIEW_LINK_REVOKE("review_link.revoke"),
    REVIEW_ROUND_CREATE("review_round.create"),
    REVIEW_ROUND_UPDATE("review_round.update"),
    REVIEW_ROUND_OPEN("review_round.open"),
    REVIEW_ENTRIES_PREPARE("review_entries.prepare"),
    REVIEW_ASSIGNMENTS_PREPARE("review_assignments.prepare"),
    INVITATION_ISSUE("invitation.issue"),
    INVITATION_REVOKE("invitation.revoke"),
    ADMIN_SIGNUP("admin.signup"),
    ADMIN_APPROVE("admin.approve"),
    ADMIN_REJECT("admin.reject"),
    LOGIN_LOCKED("auth.login_locked");

    private final String value;
}
