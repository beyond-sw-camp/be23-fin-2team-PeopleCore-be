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

    // 이메일 인증
    EMAIL_COOLDOWN(429, "1분 후 다시 요청해 주세요."),
    EMAIL_BLOCKED(429, "인증 시도 횟수를 초과했습니다. 10분 후 다시 시도해 주세요."),
    EMAIL_CODE_EXPIRED(400, "인증코드가 만료되었습니다."),
    EMAIL_CODE_MISMATCH(400, "인증코드가 일치하지 않습니다."),
    EMAIL_NOT_VERIFIED(403, "이메일 인증이 완료되지 않았습니다."),
    EMAIL_SEND_FAILED(500, "이메일 발송에 실패했습니다."),

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
    PAY_ITEM_NOT_FOUND(404, "급여항목을 찾을 수 없습니다."),

    // 간이세액표
    TAX_TABLE_NOT_FOUND(404, "해당 연도의 간이세액표를 찾을 수 없습니다."),
    TAX_TABLE_LOOKUP_FAILED(404, "해당 급여구간의 세액 정보를 찾을 수 없습니다."),

    // 퇴직연금설정
    RETIREMENT_PROVIDER_REQUIRED(400, "DB형/DB+DC형은 퇴직연금 운용사를 입력해주세요."),

    // 사원 계좌
    EMP_ACCOUNT_NOT_FOUND(404, "사원 계좌 정보를 찾을 수 없습니다."),
    EMP_ACCOUNT_NOT_REGISTERED(400, "계좌 정보가 등록되지 않은 사원이 포함되어 있습니다."),
    RETIREMENT_ACCOUNT_NOT_FOUND(404, "퇴직연금 계좌 정보를 찾을 수 없습니다."),

    // 사원 계좌 검증 (오픈뱅킹)
    ACCOUNT_VERIFY_FAILED(400, "계좌 실명조회에 실패했습니다. 입력값을 다시 확인해주세요."),
    ACCOUNT_HOLDER_MISMATCH(400, "예금주명이 계좌 정보와 일치하지 않습니다."),
    ACCOUNT_VERIFY_TOKEN_INVALID(400, "계좌 검증 정보가 없습니다. 인증을 먼저 진행해주세요."),
    ACCOUNT_VERIFY_TOKEN_EXPIRED(400, "계좌 검증이 만료되었습니다. 다시 인증해주세요."),
    ACCOUNT_VERIFY_TOKEN_MISMATCH(400, "검증한 계좌 정보와 다른 정보가 전송되었습니다. 다시 인증해주세요."),

    // 사원 퇴직연금 계좌
    RETIREMENT_SETTINGS_NOT_FOUND(404, "회사 퇴직연금 설정 정보를 찾을 수 없습니다."),
    RETIREMENT_TYPE_NOT_CHANGEABLE(400, "회사 퇴직연금 설정이 DB_DC가 아니므로 변경할 수 없습니다."),
    INVALID_RETIREMENT_TYPE(400, "유효하지 않은 퇴직연금 유형입니다. DB 또는 DC만 선택 가능합니다."),

    // 급여대장
    PAYROLL_NOT_FOUND(404, "해당 월의 급여대장을 찾을 수 없습니다."),
    PAYROLL_ALREADY_EXISTS(409, "해당 월의 급여대장이 이미 존재합니다."),
    PAYROLL_PREV_NOT_FOUND(404, "전월 급여대장을 찾을 수 없습니다."),
    PAYROLL_STATUS_INVALID(400, "현재 상태에서는 처리할 수 없습니다."),
    PAYROLL_APPROVAL_NOT_FOUND(404, "전자결재 문서를 찾을 수 없습니다."),
    UNSUPPORTED_BANK(400, "지원하지 않는 은행입니다."),
    PAYROLL_EMP_NOT_FOUND(404, "해당 직원의 급여산정정보를 찾을 수 없습니다."),
    NO_CONFIRMED_EMPLOYEES(400, "확정된 사원이 없습니다."),
    PAYROLL_EMP_ALREADY_CONFIRMED(404, "이미 확정된 사원입니다. 되돌리기 후 수정하세요."),
    OVERTIME_ALREADY_APPLIED(400, "이미 초과근무 수당이 적용되어있는 건입니다."),
    APPROVAL_SNAPSHOT_NOT_FOUND(404, "전자결재 기록(스냅샷)을 찾을 수 없습니다."),
    NO_PAYABLE_EMPLOYEES(404, "지급가능한 사원이 없습니다. (결재 승인된 사원만 지급 가능)"),
    NO_TRANSFER_TARGETS(404,"이체 대상 사원이 비어 있습니다"),
    PAYROLL_EMP_NOT_APPROVED(400, "결재 승인되지 않은 사원이 포함되어 있습니다"),
    TRANSFER_FILE_GENERATION_FAILED(500, "이체파일 생성 중 오류가 발생했습니다."),
    PAYROLL_EMP_NOT_CONFIRMABLE(400, "이미 확정/결재중/승인/지급 상태인 사원은 다시 확정할 수 없습니다."),


    // ── 정산보험료 ──
    INSURANCE_SETTLEMENT_NOT_FOUND(404, "정산보험료 데이터가 존재하지 않습니다."),
    INSURANCE_PAY_ITEM_NOT_FOUND(404, "보험 공제항목(국민연금/건강보험/장기요양/고용보험)이 등록되지 않았습니다."),
    INSURANCE_SETTLEMENT_ALREADY_APPLIED(400, "이미 급여대장에 반영된 정산 건입니다."),

    //    전자결재 연동
    OVERTIME_NOT_FOUND(404, "해당 초과근무 신청을 찾을 수 없습니다."),

    // PayItems isSystem 보호
    SYSTEM_PAY_ITEM_NOT_EDITABLE(400, "시스템 급여항목은 수정할 수 없습니다"),
    SYSTEM_PAY_ITEM_NOT_DELETABLE(400, "시스템 급여항목은 삭제할 수 없습니다"),
    PROTECTED_PAY_ITEM_NOT_EDITABLE(400, "보호 항목은 수정할 수 없습니다."),
    PROTECTED_PAY_ITEM_NOT_DELETABLE(400,"법정수당 산정 기초 항목은 삭제할 수 없습니다."),
    APPROVAL_TEMPLATE_NOT_FOUND(404, "해당 전자결재 양식을 찾을 수 없습니다."),
    APPROVAL_ACCESS_DENIED(403,"결재 권한이 없습니다."),
    APPROVAL_NOT_ROLE(403,"결재자만 승인 가능합니다."),
    APPROVAL_ALREADY_APPROVED(403,"이미 승인된 결재선입니다."),
    APPROVAL_PRE_APPROVAL_NOT_ALLOWED(403, "이 양식은 전결이 허용되지 않습니다."),


    // 연차수당
    LEAVE_ALLOWANCE_NOT_ENABLED(404, "연차수당 법정수당 항목이 설정되어 있지 않습니다."),
    LEAVE_ALLOWANCE_NOT_FOUND(404, "해당 연차수당 산정 건을 찾을 수 없습니다."),
    LEAVE_ALLOWANCE_NOT_CALCULATED(404, "산정이 완료되지 않은 건은 급여대장에 반영할 수 없습니다."),
    LEAVE_ALLOWANCE_ALREADY_APPLIED(400, "이미 급여대장에 반영된 건입니다."),
    LEAVE_ALLOWANCE_NO_RESIGN_DATE(404, "퇴직일이 설정되지 않은 사원입니다."),

    // 연차수당 (입사일 기준)
    EMPLOYEE_HIRE_DATE_NOT_FOUND(404, "사원의 입사일 정보가 없습니다."),

    // 퇴직금
    SEVERANCE_NOT_FOUND(404, "퇴직금 대장을 찾을 수 없습니다."),
    SEVERANCE_STATUS_INVALID(400, "퇴직금 상태가 유효하지 않습니다."),
    RESIGN_DATE_NOT_SET(404, "퇴직일이 설정되지 않았습니다."),
    SERVICE_PERIOD_TOO_SHORT(404, "근속기간이 1년 미만입니다."),
    LEAVE_ALLOWANCE_TYPE_INVALID(400, "연차수당 유형이 유효하지 않습니다."),
    EMPLOYEE_RETIREMENT_TYPE_NOT_SET(400, "사원의 퇴직금 설정이 되어있지 않습니다."),
    TAX_YEAR_NOT_SUPPORTED(404,"해당 연도의 퇴직소득세 계산 설정이 없습니다. TaxYearlyConfig 업데이트가 필요합니다."),
    TAX_CALCULATION_FAILED(500,"퇴직소득세 산출 중 오류가 발생했습니다."),
    SEVERANCE_NO_PAYROLL_DATA(400, "직전 3개월 급여 데이터가 없어 퇴직금을 산정할 수 없습니다. 급여대장이 확정·지급된 상태인지 확인해주세요."),
    SEVERANCE_LOCKED(409, "이미 확정/결재/지급 단계에 있어 재산정할 수 없습니다."),

//    퇴직급여 지출결의서
    INVALID_REQUEST(400, "요청 파라미터가 유효하지 않습니다."),
    SEVERANCE_ALREADY_IN_APPROVAL(409, "이미 결재가 진행 중인 퇴직금입니다."),
    APPROVAL_FORM_INVALID(400, "결재 양식 템플릿이 유효하지 않습니다."),

//    퇴직연금DC형 적립
    EMPLOYEE_NOT_DC(400, "DC형 사원만 수동 적립 등록이 가능합니다."),
    DEPOSIT_ALREADY_EXISTS(409, "동일 사원·동일 월에 이미 적립된 건이 있습니다."),
    DEPOSIT_NOT_FOUND(404, "적립 내역을 찾을 수 없습니다."),
    DEPOSIT_ALREADY_CANCELED(400, "이미 취소된 적립입니다."),
    EXCEL_GENERATION_FAILED(500, "엑셀 파일 생성에 실패했습니다."),

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

    // 퇴직연금 계좌 (사원 신규등록)
    RETIREMENT_TYPE_NOT_ALLOWED(400, "회사 퇴직연금 정책으로 인해 해당 유형은 선택할 수 없습니다."),
    RETIREMENT_ACCOUNT_REQUIRED(400, "DC형 퇴직연금은 사원 본인 계좌번호가 필요합니다."),

    //    연봉
    SALARY_CONTRACT_NOT_FOUND(404, "계약서를 찾을 수 없습니다."),
    SALARY_CONTRACT_ALREADY_DELETED(400, "이미 삭제된 계약서입니다."),
    EMPLOYEE_NOT_RESIGNED(400, "퇴직 상태인 사원의 계약서만 삭제할 수 있습니다."),
    ANNUAL_SALARY_BELOW_MINIMUM(400, "연봉이 고정수당 합계(월) × 12 미만입니다."),
    ANNUAL_SALARY_MISMATCH(400, "연봉이 자동 산출값(고정수당 합 × 12 + 비고정수당 합)과 일치하지 않습니다."),
    FILE_UPLOAD_FAILED(500, "파일 업로드에 실패했습니다."),
    FILE_NOT_FOUND(404, "첨부 파일이 없습니다."),
    FILE_DOWNLOAD_FAILED(500, "파일 다운로드에 실패했습니다."),

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
    VACATION_RULE_NOT_FOUND(404, "연차 발생 규칙이 존재하지 않습니다."),

    OVERTIME_REQUEST_NOT_FOUND(404, "초과근무 신청을 찾을 수 없습니다"),
    VACATION_REQ_NOT_FOUND(404, "휴가 신청을 찾을 수 없습니다"),
    OVERTIME_EXCEEDS_WEEKLY_MAX(400, "주간 최대 근무시간을 초과하여 신청할 수 없습니다"),
    OVERTIME_POLICY_NOT_FOUND(404, "초과근무 정책이 설정되지 않은 회사입니다."),

    /*회사 허용 IP*/
    ALLOWED_IP_NOT_FOUND(404, "허용 IP를 찾을 수 없습니다."),
    ALLOWED_IP_DUPLICATE(409, "이미 등록된 IP 대역입니다."),
    INVALID_CIDR_FORMAT(400, "유효하지 않은 CIDR 형식입니다."),

    /* 출퇴근 체크인/아웃 */
    COMMUTE_ALREADY_CHECKED_IN(409, "이미 오늘 출근 체크가 완료되었습니다."),
    COMMUTE_NOT_CHECKED_IN(404, "오늘 출근 기록이 없어 퇴근 체크를 할 수 없습니다."),
    COMMUTE_ALREADY_CHECKED_OUT(409, "이미 오늘 퇴근 체크가 완료되었습니다."),
    COMMUTE_IP_NOT_ALLOWED(403, "회사가 허용한 IP에서만 출퇴근 체크가 가능합니다."),
    EMPLOYEE_WORK_GROUP_NOT_ASSIGNED(409, "사원에게 근무 그룹이 배정되지 않았습니다."),

    /* 근태 정정 */
    ATTENDANCE_MODIFY_NOT_FOUND(404, "근태 정정 신청을 찾을 수 없습니다."),
    ATTENDANCE_MODIFY_PENDING_EXISTS(409, "진행 중인 정정 신청이 있습니다."),
    ATTENDANCE_RECORD_NOT_FOUND(404, "해당 날짜 출근 기록이 없습니다."),
    ATTENDANCE_MODIFY_FORM_NOT_FOUND(404, "근태 정정 양식이 존재하지 않습니다."),
    ATTENDANCE_MODIFY_APPLY_FAILED(500, "근태 정정 적용 중 오류가 발생했습니다."),

    /*전자 결재 */
    COMPANY_INIT_EVENT_PUBLISH_FAILED(404,"회사 생성 이벤트를 실행하였습니다."),

    /*휴가 */
    INVALID_REQUEST_STATUS_TRANSITION(400, "허용되지 않은 휴가 신청 상태 전이입니다."),
    VACATION_POLICY_FIRST_NOTICE_REQUIRED(400, "연차 촉진 사용 시 1차 통지 시기는 필수입니다."),
    VACATION_POLICY_NOTICE_ORDER_INVALID(400, "2차 통지는 1차 통지보다 만료일에 가까워야 합니다."),
    VACATION_BALANCE_CAP_EXCEEDED(409, "연 최대 적립 일수를 초과했습니다."),
    VACATION_BALANCE_INSUFFICIENT(409, "휴가 잔여가 부족합니다."),
    VACATION_BALANCE_NOT_FOUND(404, "휴가 잔여 정보를 찾을 수 없습니다."),
    VACATION_BALANCE_PENDING_INSUFFICIENT(500, "잔여 대기 일수 정합성 오류 — 관리자 문의 필요."),
    VACATION_BALANCE_USED_INSUFFICIENT(500, "잔여 사용 일수 정합성 오류 — 관리자 문의 필요."),
    VACATION_TYPE_SYSTEM_RESERVED(400, "시스템 예약 휴가 유형은 변경/삭제할 수 없습니다."),
    VACATION_TYPE_CODE_DUPLICATE(409, "이미 존재하는 휴가 유형 코드입니다."),
    VACATION_TYPE_NOT_FOUND(404, "존재하지 않는 휴가 종류입니다."),
    VACATION_TYPE_IN_USE(409, "해당 유형을 사용 중인 잔여/신청이 있어 삭제할 수 없습니다."),
    VACATION_RULE_OVERLAP(409, "근속 구간이 기존 규칙과 겹칩니다."),
    VACATION_TYPE_GENDER_NOT_ALLOWED(403, "해당 휴가 유형은 성별 제한으로 신청할 수 없습니다."),
    VACATION_REQ_PREGNANCY_WEEKS_REQUIRED(400, "유산·사산휴가는 임신 주수 입력이 필요합니다."),
    VACATION_REQ_OFFICIAL_REASON_REQUIRED(400, "공가 신청 시 하위 사유를 선택해야 합니다."),
    VACATION_REQ_PROOF_REQUIRED(400, "증빙 파일 첨부가 필요한 휴가 유형입니다."),
    VACATION_REQ_SPOUSE_BIRTH_EXPIRED(400, "배우자 출산휴가는 출산일 기준 90일 이내에만 사용할 수 있습니다."),
    VACATION_REQ_BIRTH_DATE_REQUIRED(400, "배우자 출산휴가는 출산일(출산 예정일) 입력이 필요합니다."),
    VACATION_REQ_PREGNANCY_WEEKS_INVALID(400, "임신 주수는 1 이상이어야 합니다."),
    VACATION_REQ_DAYS_MISMATCH(400, "요청 일수와 유형별 자동 산정 일수가 일치하지 않습니다."),
    VACATION_REQ_ITEMS_EMPTY(400, "휴가 슬롯(vacReqItems)이 비어있습니다."),
    CONCURRENT_REQUEST_LOCK_FAILED(409, "동시 요청 처리 중입니다. 잠시 후 다시 시도해주세요."),

    /* 배치 관리자 */
    BATCH_JOB_NOT_SUPPORTED(400, "지원하지 않는 배치 Job 입니다."),
    BATCH_JOB_NOT_FOUND(404, "배치 Job Bean 을 찾을 수 없습니다."),
    BATCH_PARAMETER_INVALID(400, "배치 재실행 파라미터가 올바르지 않습니다."),
    BATCH_RERUN_FAILED(500, "배치 재실행에 실패했습니다."),
    BATCH_TENANT_FORBIDDEN(403, "다른 회사의 배치에 접근할 수 없습니다.");


    private final int status;
    private final String message;
    }