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
    CONTEST_JUDGE_DELETE("contest_judge.delete"),
    REVIEW_LINK_ISSUE("review_link.issue"),
    REVIEW_LINK_REVOKE("review_link.revoke"),
    REVIEW_ROUND_CREATE("review_round.create"),
    REVIEW_ROUND_UPDATE("review_round.update"),
    REVIEW_ROUND_DEADLINE_EXTEND("review_round.deadline_extend"),
    REVIEW_ROUND_OPEN("review_round.open"),
    REVIEW_ROUND_FINALIZE("review_round.finalize"),
    REVIEW_ENTRIES_PREPARE("review_entries.prepare"),
    REVIEW_ENTRIES_RESET("review_entries.reset"),
    REVIEW_ASSIGNMENTS_PREPARE("review_assignments.prepare"),
    REVIEW_ASSIGNMENT_CANCEL("review_assignment.cancel"),
    REVIEW_ASSIGNMENT_REASSIGN("review_assignment.reassign"),
    REVIEW_ASSIGNMENT_DUE_AT_UPDATE("review_assignment.due_at_update"),
    TEAM_STATUS_CHANGE("team.status_change"),
    TEAM_FINALIZE("team.finalize"),
    AWARD_CALCULATE("award.calculate"),
    AWARD_CONFIRM("award.confirm"),
    AWARD_STATUS_CHANGE("award.status_change"),
    INVITATION_ISSUE("invitation.issue"),
    INVITATION_REVOKE("invitation.revoke"),
    ADMIN_SIGNUP("admin.signup"),
    ADMIN_APPROVE("admin.approve"),
    ADMIN_REJECT("admin.reject"),
    LOGIN_LOCKED("auth.login_locked"),
    EVIDENCE_REVIEW("evidence.review"),
    EVIDENCE_DECISION("evidence.decision"),
    EVIDENCE_FILE_DOWNLOAD("evidence.file_download");

    private final String value;
}
