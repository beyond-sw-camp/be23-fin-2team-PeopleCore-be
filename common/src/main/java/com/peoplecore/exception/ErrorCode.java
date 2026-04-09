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

    // SuperAdmin 생성
    INSURANCE_JOB_TYPE_NOT_FOUND(404, "업종을 찾을 수 없습니다"),

    // 급여지급설정
    PAY_SETTINGS_NOT_FOUND(404, "급여지급설정을 찾을 수 없습니다."),
    PAY_INVALID_PAYMENT_DAY(400, "지급일은 1~31 사이여야 합니다."),
    PAY_INVALID_BANK_CODE(400, "유효하지 않은 은행 코드입니다."),
    PAY_LAST_DAY_CONFLICT(400, "말일 선택 시 지급일 값은 입력하지 마세요."),

    // 사회보험요율
    INSURANCE_RATES_NOT_FOUND(404, "해당 연도의 보험요율을 찾을 수 없습니다."),
    INSURANCE_JOB_TYPE_DUPLICATE(409, "이미 존재하는 업종명입니다."),

//    급여항목
    PAY_ITEM_IN_USE(409, "사용 중인 급여항목은 삭제할 수 없습니다."),
    INSURANCE_JOB_TYPE_IN_USE(409, "사원에 배정된 업종은 삭제할 수 없습니다."),

    // 간이세액표
    TAX_TABLE_NOT_FOUND(404, "해당 연도의 간이세액표를 찾을 수 없습니다."),
    TAX_TABLE_LOOKUP_FAILED(404, "해당 급여구간의 세액 정보를 찾을 수 없습니다."),

    // 퇴직연금설정
    RETIREMENT_PROVIDER_REQUIRED(400, "DB형/DB+DC형은 퇴직연금 운용사를 입력해주세요."),

    // 공통
    NOT_FOUND(404, "리소스를 찾을 수 없습니다."),
    BAD_REQUEST(400, "잘못된 요청입니다."),
    INTERNAL_SERVER_ERROR(500, "서버 내부 오류가 발생했습니다."),

//    사원관리
    EMPLOYEE_NOT_FOUND(404, "사원을 찾을 수 없습니다."),
    GRADE_NOT_FOUND(404, "직급을 찾을 수 없습니다."),
    TITLE_NOT_FOUND(404, "직책을 찾을 수 없습니다."),
    MANUAL_PASSWORD_REQUIRED(400, "비밀번호를 직접 입력해야 합니다."),


//    연봉
    SALARY_CONTRACT_NOT_FOUND(404, "계약서를 찾을 수 없습니다."),
    SALARY_CONTRACT_ALREADY_DELETED(400, "이미 삭제된 계약서입니다."),
    EMPLOYEE_NOT_RESIGNED(400, "퇴직 상태인 사원의 계약서만 삭제할 수 있습니다."),
    FILE_UPLOAD_FAILED(500, "파일 업로드에 실패했습니다.");


    private final int status;
    private final String message;
}