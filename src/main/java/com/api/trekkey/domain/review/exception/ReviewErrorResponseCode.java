package com.api.trekkey.domain.review.exception;

import com.api.trekkey.global.response.code.BaseResponseCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ReviewErrorResponseCode implements BaseResponseCode {
    CONTEST_JUDGE_NOT_FOUND(
            "CONTEST_JUDGE_NOT_FOUND",
            404,
            "심사위원을 찾을 수 없습니다."),
    CONTEST_JUDGE_DUPLICATED(
            "CONTEST_JUDGE_DUPLICATED",
            409,
            "이미 등록된 심사위원입니다."),
    CONTEST_JUDGE_USER_INVALID(
            "CONTEST_JUDGE_USER_INVALID",
            400,
            "심사위원으로 연결할 수 없는 사용자입니다."),
    CONTEST_JUDGE_HAS_ASSIGNMENTS(
            "CONTEST_JUDGE_HAS_ASSIGNMENTS",
            409,
            "심사 배정 이력이 있는 심사위원은 수정하거나 삭제할 수 없습니다."),
    REVIEW_LINK_EXPIRATION_INVALID(
            "REVIEW_LINK_EXPIRATION_INVALID",
            400,
            "심사 링크 만료 시각은 현재보다 이후여야 합니다."),
    REVIEW_LINK_INVALID(
            "REVIEW_LINK_INVALID",
            401,
            "유효하지 않거나 만료된 심사 링크입니다."),
    REVIEW_ROUND_NOT_FOUND(
            "REVIEW_ROUND_NOT_FOUND",
            404,
            "심사 라운드를 찾을 수 없습니다."),
    REVIEW_ROUND_DUPLICATED(
            "REVIEW_ROUND_DUPLICATED",
            409,
            "같은 순서의 심사 라운드가 이미 존재합니다."),
    REVIEW_ROUND_SEQUENCE_INVALID(
            "REVIEW_ROUND_SEQUENCE_INVALID",
            409,
            "심사 라운드 순서는 1부터 빈 번호 없이 이어져야 합니다."),
    REVIEW_ROUND_CONFIGURATION_LOCKED(
            "REVIEW_ROUND_CONFIGURATION_LOCKED",
            409,
            "진행 중이거나 확정된 심사 라운드의 설정은 변경할 수 없습니다."),
    REVIEW_ROUND_CONFIGURATION_INVALID(
            "REVIEW_ROUND_CONFIGURATION_INVALID",
            409,
            "심사 라운드를 시작하기 위한 설정이 올바르지 않습니다."),
    REVIEW_ROUND_STATUS_TRANSITION_INVALID(
            "REVIEW_ROUND_STATUS_TRANSITION_INVALID",
            409,
            "허용되지 않는 심사 라운드 상태 변경입니다."),
    REVIEW_ROUND_FINALIZATION_NOT_ALLOWED(
            "REVIEW_ROUND_FINALIZATION_NOT_ALLOWED",
            409,
            "진행 중인 심사 라운드만 확정할 수 있습니다."),
    REVIEW_ROUND_ASSIGNMENTS_INCOMPLETE(
            "REVIEW_ROUND_ASSIGNMENTS_INCOMPLETE",
            409,
            "완료되지 않은 심사 배정이 있어 라운드를 확정할 수 없습니다."),
    REVIEW_ROUND_RESULT_INVALID(
            "REVIEW_ROUND_RESULT_INVALID",
            409,
            "심사 라운드 결과를 확정할 수 없는 상태입니다."),
    REVIEW_MANUAL_DECISION_INVALID(
            "REVIEW_MANUAL_DECISION_INVALID",
            400,
            "수동 판정 요청이 올바르지 않습니다."),
    REVIEW_ROUND_OPEN_WINDOW_EXPIRED(
            "REVIEW_ROUND_OPEN_WINDOW_EXPIRED",
            409,
            "종료 시각이 지난 심사 라운드는 시작할 수 없습니다."),
    REVIEW_ROUND_DEADLINE_INVALID(
            "REVIEW_ROUND_DEADLINE_INVALID",
            400,
            "새 종료 시각은 현재 시각과 기존 종료 시각보다 이후여야 합니다."),
    REVIEW_ROUND_SUBMISSION_WINDOW_INVALID(
            "REVIEW_ROUND_SUBMISSION_WINDOW_INVALID",
            409,
            "첫 심사 라운드는 제출 마감 이후에 시작해야 합니다."),
    REVIEW_ROUND_PREVIOUS_NOT_FINALIZED(
            "REVIEW_ROUND_PREVIOUS_NOT_FINALIZED",
            409,
            "앞선 심사 라운드를 모두 확정한 뒤 다음 라운드를 시작할 수 있습니다."),
    REVIEW_ROUND_ALREADY_OPEN(
            "REVIEW_ROUND_ALREADY_OPEN",
            409,
            "같은 대회에서 두 개의 심사 라운드를 동시에 진행할 수 없습니다."),
    REVIEW_ROUND_CRITERION_NOT_FOUND(
            "REVIEW_ROUND_CRITERION_NOT_FOUND",
            404,
            "심사 라운드의 평가 기준을 찾을 수 없습니다."),
    REVIEW_ROUND_CRITERION_INVALID(
            "REVIEW_ROUND_CRITERION_INVALID",
            400,
            "평가 기준의 코드, 이름, 배점 또는 순서가 올바르지 않습니다."),
    REVIEW_ROUND_CRITERION_CODE_IMMUTABLE(
            "REVIEW_ROUND_CRITERION_CODE_IMMUTABLE",
            409,
            "저장된 평가 기준 코드는 변경할 수 없습니다."),
    REVIEW_ROUND_CRITERION_DUPLICATED(
            "REVIEW_ROUND_CRITERION_DUPLICATED",
            409,
            "같은 심사 라운드에 중복된 평가 기준이 있습니다."),
    REVIEW_ROUND_CRITERION_REQUIRED(
            "REVIEW_ROUND_CRITERION_REQUIRED",
            409,
            "심사 라운드를 시작하려면 활성 평가 기준이 하나 이상 필요합니다."),
    REVIEW_ROUND_REQUIRED(
            "REVIEW_ROUND_REQUIRED",
            409,
            "심사 설정과 상태 변경은 심사 라운드 API에서 처리해야 합니다."),
    REVIEW_ROUND_ENTRY_INVALID(
            "REVIEW_ROUND_ENTRY_INVALID",
            409,
            "심사 라운드를 시작할 수 없는 상태의 심사 대상이 포함되어 있습니다."),
    REVIEW_ENTRY_PREPARATION_NOT_ALLOWED(
            "REVIEW_ENTRY_PREPARATION_NOT_ALLOWED",
            409,
            "준비 중인 심사 라운드에서만 심사 대상을 생성할 수 있습니다."),
    REVIEW_ENTRY_PREVIOUS_ROUND_REQUIRED(
            "REVIEW_ENTRY_PREVIOUS_ROUND_REQUIRED",
            409,
            "바로 이전 심사 라운드가 확정되어야 다음 라운드 대상을 준비할 수 있습니다."),
    REVIEW_ENTRY_PREVIOUS_SELECTION_REQUIRED(
            "REVIEW_ENTRY_PREVIOUS_SELECTION_REQUIRED",
            409,
            "바로 이전 심사 라운드에 선정된 제출물이 없습니다."),
    REVIEW_ENTRY_MANUAL_SUBMISSIONS_REQUIRED(
            "REVIEW_ENTRY_MANUAL_SUBMISSIONS_REQUIRED",
            400,
            "수동 심사 대상 제출물을 하나 이상 선택해야 합니다."),
    REVIEW_ENTRY_SUBMISSION_REQUIRED(
            "REVIEW_ENTRY_SUBMISSION_REQUIRED",
            409,
            "심사 대상으로 등록할 제출 완료 작품이 없습니다."),
    REVIEW_ENTRY_SUBMISSION_INVALID(
            "REVIEW_ENTRY_SUBMISSION_INVALID",
            409,
            "심사 대상으로 확정할 수 없는 제출물이 포함되어 있습니다."),
    REVIEW_ENTRY_TEAM_NOT_FINALIZED(
            "REVIEW_ENTRY_TEAM_NOT_FINALIZED",
            409,
            "심사 대상 팀의 참가 명단을 먼저 확정해야 합니다."),
    REVIEW_ENTRY_DUPLICATED(
            "REVIEW_ENTRY_DUPLICATED",
            409,
            "심사 대상이 이미 등록되어 있습니다."),
    REVIEW_ENTRY_SYNC_REQUIRED(
            "REVIEW_ENTRY_SYNC_REQUIRED",
            409,
            "제출 현황이 변경되었습니다. 심사 대상을 다시 동기화한 뒤 라운드를 시작해 주세요."),
    REVIEW_ENTRY_RESET_NOT_ALLOWED(
            "REVIEW_ENTRY_RESET_NOT_ALLOWED",
            409,
            "채점 이력이 있는 심사 대상은 초기화할 수 없습니다."),
    REVIEW_ENTRY_REQUIRED(
            "REVIEW_ENTRY_REQUIRED",
            409,
            "심사 라운드를 시작하려면 심사 대상을 먼저 준비해야 합니다."),
    REVIEW_ASSIGNMENT_PREPARATION_NOT_ALLOWED(
            "REVIEW_ASSIGNMENT_PREPARATION_NOT_ALLOWED",
            409,
            "준비 중이거나 진행 중인 심사 라운드에서만 심사위원을 배정할 수 있습니다."),
    REVIEW_ASSIGNMENT_ENTRY_REQUIRED(
            "REVIEW_ASSIGNMENT_ENTRY_REQUIRED",
            409,
            "심사위원에게 배정할 심사 대상이 없습니다."),
    REVIEW_ASSIGNMENT_ENTRY_INVALID(
            "REVIEW_ASSIGNMENT_ENTRY_INVALID",
            409,
            "현재 상태에서는 배정할 수 없는 심사 대상이 포함되어 있습니다."),
    REVIEW_ASSIGNMENT_REQUIRED(
            "REVIEW_ASSIGNMENT_REQUIRED",
            409,
            "심사 라운드를 시작하려면 모든 심사 대상에 심사위원을 한 명 이상 배정해야 합니다."),
    REVIEW_ASSIGNMENT_DUE_AT_INVALID(
            "REVIEW_ASSIGNMENT_DUE_AT_INVALID",
            400,
            "심사 배정 마감 시각이 올바르지 않습니다."),
    REVIEW_ASSIGNMENT_DUPLICATED(
            "REVIEW_ASSIGNMENT_DUPLICATED",
            409,
            "심사 대상 배정이 이미 처리되었습니다."),
    REVIEW_ASSIGNMENT_MANAGEMENT_NOT_ALLOWED(
            "REVIEW_ASSIGNMENT_MANAGEMENT_NOT_ALLOWED",
            409,
            "준비 중이거나 진행 중인 심사 라운드의 배정만 변경할 수 있습니다."),
    REVIEW_ASSIGNMENT_STATUS_TRANSITION_INVALID(
            "REVIEW_ASSIGNMENT_STATUS_TRANSITION_INVALID",
            409,
            "현재 심사 배정 상태에서는 요청한 변경을 처리할 수 없습니다."),
    REVIEW_ASSIGNMENT_NOT_FOUND(
            "REVIEW_ASSIGNMENT_NOT_FOUND",
            404,
            "채점할 심사 배정을 찾을 수 없습니다."),
    REVIEW_SUBMISSION_NOT_ALLOWED(
            "REVIEW_SUBMISSION_NOT_ALLOWED",
            409,
            "현재 대회 또는 심사 대상 상태에서는 채점을 제출할 수 없습니다."),
    REVIEW_SUBMISSION_NOT_OPEN(
            "REVIEW_SUBMISSION_NOT_OPEN",
            409,
            "현재 채점을 제출할 수 있는 심사 시간이 아닙니다."),
    REVIEW_SUBMISSION_DEADLINE_EXPIRED(
            "REVIEW_SUBMISSION_DEADLINE_EXPIRED",
            409,
            "심사 배정 마감 시각이 지났습니다."),
    REVIEW_SCORE_CRITERIA_MISMATCH(
            "REVIEW_SCORE_CRITERIA_MISMATCH",
            400,
            "현재 라운드의 모든 평가 기준에 점수를 한 번씩 입력해야 합니다."),
    REVIEW_SCORE_OUT_OF_RANGE(
            "REVIEW_SCORE_OUT_OF_RANGE",
            400,
            "평가 점수가 허용된 범위를 벗어났습니다."),
    REVIEW_COMMENT_TOO_LONG(
            "REVIEW_COMMENT_TOO_LONG",
            400,
            "심사 의견은 5000자 이하로 입력해야 합니다."),
    REVIEW_ALREADY_SUBMITTED(
            "REVIEW_ALREADY_SUBMITTED",
            409,
            "이미 제출된 채점 결과와 다른 내용으로 다시 제출할 수 없습니다."),
    REVIEW_SUBMISSION_STATE_INVALID(
            "REVIEW_SUBMISSION_STATE_INVALID",
            409,
            "저장된 심사 배정과 채점 결과의 상태가 일치하지 않습니다."),
    REVIEW_SUBMISSION_DUPLICATED(
            "REVIEW_SUBMISSION_DUPLICATED",
            409,
            "채점 결과가 이미 처리되었습니다.");

    private final String code;
    private final int httpStatus;
    private final String message;
}
