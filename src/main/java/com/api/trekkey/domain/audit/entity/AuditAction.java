package com.api.trekkey.domain.audit.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AuditAction {
    CONTEST_CREATE("contest.create"),
    CONTEST_UPDATE("contest.update"),
    STAGE_STATUS_CHANGE("stage.status_change"),
    TEAM_STATUS_CHANGE("team.status_change"),
    TEAM_FINALIZE("team.finalize"),
    JUDGE_INVITE("judge.invite"),
    JUDGE_DELETE("judge.delete"),
    ROUND_OPEN("stage.round_open"),
    ROUND_FINALIZE("stage.round_finalize"),
    ENTRY_DECIDE("entry.manual_decide"),
    INVITATION_ISSUE("invitation.issue"),
    INVITATION_REVOKE("invitation.revoke"),
    ADMIN_SIGNUP("admin.signup"),
    ADMIN_APPROVE("admin.approve"),
    ADMIN_REJECT("admin.reject"),
    LOGIN_LOCKED("auth.login_locked");

    private final String value;
}
