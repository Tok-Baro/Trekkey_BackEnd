package com.api.template.global.response.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorResponseCode implements BaseResponseCode {
    BAD_REQUEST("GLOBAL_400", 400, "잘못된 요청입니다."),
    INVALID_HTTP_MESSAGE_BODY("GLOBAL_400_BODY", 400, "HTTP 요청 바디의 형식이 잘못되었습니다."),
    INVALID_HTTP_MESSAGE_PARAMETER("GLOBAL_400_PARAMETER", 400, "HTTP 요청 파라미터의 형식이 잘못되었습니다."),
    UNAUTHORIZED("GLOBAL_401", 401, "인증이 필요합니다."),
    FORBIDDEN("GLOBAL_403", 403, "해당 요청에 대한 접근 권한이 없습니다."),
    NOT_FOUND_ENDPOINT("GLOBAL_404", 404, "존재하지 않는 엔드포인트입니다."),
    UNSUPPORTED_HTTP_METHOD("GLOBAL_405", 405, "지원하지 않는 HTTP 메서드입니다."),
    SERVER_ERROR("GLOBAL_500", 500, "서버 내부에서 알 수 없는 에러가 발생했습니다.");

    private final String code;
    private final int httpStatus;
    private final String message;
}
