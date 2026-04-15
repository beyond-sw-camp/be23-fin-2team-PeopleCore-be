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

    // 인사통합 PIN
    HR_ADMIN_SCOPE_REQUIRED(403, "인사통합 PIN 인증이 필요합니다."),
    HR_ADMIN_PIN_NOT_SET(404, "인사통합 PIN이 설정되지 않았습니다."),
    HR_ADMIN_PIN_MISMATCH(401, "PIN이 일치하지 않습니다."),
    HR_ADMIN_PIN_ALREADY_SET(409, "이미 PIN이 설정되어 있습니다."),

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

    // 사원 계좌
    EMP_ACCOUNT_NOT_FOUND(404, "사원 계좌 정보를 찾을 수 없습니다."),
    RETIREMENT_ACCOUNT_NOT_FOUND(404, "퇴직연금 계좌 정보를 찾을 수 없습니다."),

    //    사원 퇴직연금 계좌
    RETIREMENT_SETTINGS_NOT_FOUND(404, "회사 퇴직연금 설정 정보를 찾을 수 없습니다."),
    RETIREMENT_TYPE_NOT_CHANGEABLE(400, "회사 퇴직연금 설정이 DB_DC가 아니므로 변경할 수 없습니다."),
    INVALID_RETIREMENT_TYPE(400, "유효하지 않은 퇴직연금 유형입니다. DB 또는 DC만 선택 가능합니다."),

    // ── 정산보험료 ──
    INSURANCE_SETTLEMENT_NOT_FOUND(404, "정산보험료 데이터가 존재하지 않습니다."),
    INSURANCE_PAY_ITEM_NOT_FOUND(404, "보험 공제항목(국민연금/건강보험/장기요양/고용보험)이 등록되지 않았습니다."),
    INSURANCE_SETTLEMENT_ALREADY_APPLIED(400, "이미 급여대장에 반영된 정산 건입니다."),

    PAYROLL_NOT_FOUND(400, "급여산정 데이터가 존재하지 않습니다."),
    PAYROLL_STATUS_INVALID(409, "확정된 급여의 보험료는 재산정 불가합니다."),

    // PayItems isSystem 보호
    SYSTEM_PAY_ITEM_NOT_EDITABLE(400, "시스템 급여항목은 수정할 수 없습니다"),
    SYSTEM_PAY_ITEM_NOT_DELETABLE(400, "시스템 급여항목은 삭제할 수 없습니다"),

    // 캘린더
    CALENDAR_NOT_FOUND(404, "캘린더를 찾을 수 없습니다."),
    CALENDAR_NAME_DUPLICATE(409, "이미 같은 이름의 캘린더가 존재합니다."),
    CALENDAR_OWNER_MISMATCH(403, "본인의 캘린더만 관리할 수 있습니다."),
    DEFAULT_CALENDAR_CANNOT_DELETE(400, "기본 캘린더는 삭제할 수 없습니다."),
    DEFAULT_CALENDAR_CANNOT_RENAME(400, "기본 캘린더의 이름은 변경할 수 없습니다."),

    // 일정
    EVENT_NOT_FOUND(404, "일정을 찾을 수 없습니다."),
    EVENT_DELETED(404, "삭제된 일정입니다."),
    EVENT_ACCESS_DENIED(403, "접근 권한이 없습니다."),
    EVENT_OWNER_MISMATCH(403, "본인의 일정만 수정/삭제할 수 있습니다."),
    EVENT_REGISTER_DENIED(403, "본인의 캘린더에만 일정을 등록할 수 있습니다."),

    // 관심 캘린더 / 공유 요청
    INTEREST_CALENDAR_NOT_FOUND(404, "관심 캘린더를 찾을 수 없습니다."),
    INTEREST_CALENDAR_OWNER_MISMATCH(403, "본인의 관심 캘린더만 관리할 수 있습니다."),
    SHARE_REQUEST_NOT_FOUND(404, "공유 요청을 찾을 수 없습니다."),
    SHARE_REQUEST_SELF(400, "본인에게는 공유 요청을 보낼 수 없습니다."),
    SHARE_REQUEST_DUPLICATE(409, "이미 대기 중인 요청이 있습니다."),
    SHARE_REQUEST_ALREADY_PROCESSED(400, "이미 처리된 요청입니다."),
    SHARE_REQUEST_ACCESS_DENIED(403, "본인에게 온 요청만 처리할 수 있습니다."),

    //    전사 캘린더
    COMPANY_EVENT_NOT_FOUND(404, "전사 일정을 찾을 수 없습니다."),
    COMPANY_EVENT_NOT_COMPANY(400, "전사 일정이 아닙니다."),


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
    FILE_UPLOAD_FAILED(500, "파일 업로드에 실패했습니다."),

    /*워크 그룹 */
    WORK_GROUP_NOT_FOUND(404, "근무 그룹을 찾을 수 없습니다."),
    WORK_GROUP_CODE_DUPLICATE(409, "이미 존재하는 근무 그룹 코드입니다."),
    WORK_GROUP_HAS_MEMBERS(409, "소속된 멤버가 있어 삭제할 수 없습니다."),

    /**
     * 이관 source 와 target 이 동일 그룹인 경우
     */
    WORK_GROUP_TRANSFER_SAME_TARGET(400, "이관 대상 그룹이 현재 그룹과 동일합니다."),

    /**
     * source / target 이 서로 다른 회사 소속일 경우 (타 회사로 이관 불가)
     */
    WORK_GROUP_TRANSFER_DIFFERENT_COMPANY(400, "다른 회사의 근무 그룹으로는 이관할 수 없습니다."),

    WORK_GROUP_TRANSFER_INVALID_MEMBERS(400, "이관 요청에 유효하지 않은 사원이 포함되어 있습니다."),

    /* 연차 정책 */
    VACATION_POLICY_NOT_FOUND(404, "연차 정책이 존재하지 않습니다."),
    VACATION_POLICY_DUPLICATED(409, "연차 정책이 중복 존재합니다. 관리자에게 문의하세요."),
    VACATION_POLICY_FISCAL_START_REQUIRED(400, "회계연도 시작일(mm-dd)을 지정해 주세요."),
    VACATION_POLICY_FISCAL_START_INVALID(400, "회계연도 시작일 형식이 올바르지 않습니다. (예: 01-01)"),
    VACATION_RULE_NOT_FOUND(404, "연차 발생 규칙이 존재하지 않습니다."),

    OVERTIME_REQUEST_NOT_FOUND(404, "초과근무 신청을 찾을 수 없습니다"),
    VACATION_REQ_NOT_FOUND(404, "휴가 신청을 찾을 수 없습니다"),
    OVERTIME_EXCEEDS_WEEKLY_MAX(400, "주간 최대 근무시간을 초과하여 신청할 수 없습니다"),

    /*회사 허용 IP*/
    ALLOWED_IP_NOT_FOUND(404, "허용 IP를 찾을 수 없습니다."),
    ALLOWED_IP_DUPLICATE(409, "이미 등록된 IP 대역입니다."),
    INVALID_CIDR_FORMAT(400, "유효하지 않은 CIDR 형식입니다."),

    /* 출퇴근 체크인/아웃 */
    COMMUTE_ALREADY_CHECKED_IN(409, "이미 오늘 출근 체크가 완료되었습니다."),
    COMMUTE_NOT_CHECKED_IN(404, "오늘 출근 기록이 없어 퇴근 체크를 할 수 없습니다."),
    COMMUTE_ALREADY_CHECKED_OUT(409, "이미 오늘 퇴근 체크가 완료되었습니다."),
    EMPLOYEE_WORK_GROUP_NOT_ASSIGNED(409, "사원에게 근무 그룹이 배정되지 않았습니다.");

    private final int status;
    private final String message;
}