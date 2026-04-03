package com.peoplecore.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 인증
    INVALID_CREDENTIALS(401, "이메일 또는 비밀번호가 일치하지 않습니다."),
    INVALID_TOKEN(401, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(401, "만료된 토큰입니다."),
    RESIGNED_EMPLOYEE(403, "퇴직한 사원입니다."),
    FORBIDDEN(403, "접근 권한이 없습니다."),

    // SMS 인증
    SMS_COOLDOWN(429, "1분 후 다시 요청해 주세요."),
    SMS_BLOCKED(429, "인증 시도 횟수를 초과했습니다. 10분 후 다시 시도해 주세요."),
    SMS_CODE_EXPIRED(400, "인증코드가 만료되었습니다."),
    SMS_CODE_MISMATCH(400, "인증코드가 일치하지 않습니다."),
    SMS_NOT_VERIFIED(403, "SMS 인증이 완료되지 않았습니다."),

    // 비밀번호
    SAME_PASSWORD(400, "기존 비밀번호와 동일합니다."),

    // 부서
    DEPARTMENT_NOT_FOUND(404, "부서를 찾을 수 없습니다."),
    DEPARTMENT_NAME_DUPLICATE(409, "이미 존재하는 부서명입니다."),
    DEPARTMENT_CODE_DUPLICATE(409, "이미 존재하는 부서코드입니다."),
    DEPARTMENT_HAS_MEMBERS(400, "소속 인원이 있어 삭제할 수 없습니다."),
    DEPARTMENT_HAS_CHILDREN(400, "하위 부서가 있어 삭제할 수 없습니다."),
    DEPARTMENT_CIRCULAR_REFERENCE(400, "하위 부서를 상위 부서로 지정할 수 없습니다."),

    // 공통
    NOT_FOUND(404, "리소스를 찾을 수 없습니다."),
    BAD_REQUEST(400, "잘못된 요청입니다."),
    INTERNAL_SERVER_ERROR(500, "서버 내부 오류가 발생했습니다.");

    private final int status;
    private final String message;
}