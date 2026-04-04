package com.peoplecore.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

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

//    회사 설정
    COMPANY_NOT_FOUND(404, "회사를 찾을 수 없습니다"),
    COMPANY_IP_DUPLICATE(409, "이미 등록된 IP 주소입니다"),
    INVALID_STATUS_TRANSITION(400, "허용되지 않는 상태 변경입니다"),
    INVALID_CONTRACT_DATE(400, "계약 종료일은 시작일 이후여야 합니다"),

    // SuperAdmin 생성    (직급,직책쪽 에러 추가시 중복이면 여기꺼를 삭제하셔도 됩니다)
    GRADE_NOT_FOUND(404, "직급을 찾을 수 없습니다"),
    TITLE_NOT_FOUND(404, "직책을 찾을 수 없습니다"),
    INSURANCE_JOB_TYPE_NOT_FOUND(404, "업종을 찾을 수 없습니다"),

    // 급여지급설정
    PAY_SETTINGS_NOT_FOUND(404, "급여지급설정을 찾을 수 없습니다."),
    PAY_INVALID_PAYMENT_DAY(400, "지급일은 1~31 사이여야 합니다."),
    PAY_INVALID_BANK_CODE(400, "유효하지 않은 은행 코드입니다."),
    PAY_LAST_DAY_CONFLICT(400, "말일 선택 시 지급일 값은 입력하지 마세요."),

    // 지급/공제항목 관리
//    PAY_SETTINGS_NOT_FOUND(404, "급여지급설정을 찾을 수 없습니다."),
//    PAY_INVALID_PAYMENT_DAY(400, "지급일은 1~31 사이여야 합니다."),
//    PAY_INVALID_BANK_CODE(400, "유효하지 않은 은행 코드입니다."),
//    PAY_LAST_DAY_CONFLICT(400, "말일 선택 시 지급일 값은 입력하지 마세요."),

    // 공통
    NOT_FOUND(404, "리소스를 찾을 수 없습니다."),
    BAD_REQUEST(400, "잘못된 요청입니다."),
    INTERNAL_SERVER_ERROR(500, "서버 내부 오류가 발생했습니다."),

//    사원관리
    EMPLOYEE_NOT_FOUND(404, "사원을 찾을 수 없습니다."),
    GRADE_NOT_FOUND(404, "직급을 찾을 수 없습니다."),
    TITLE_NOT_FOUND(404, "직책을 찾을 수 없습니다."),
    MANUAL_PASSWORD_REQUIRED(400, "비밀번호를 직접 입력해야 합니다.");


    private final int status;
    private final String message;
}