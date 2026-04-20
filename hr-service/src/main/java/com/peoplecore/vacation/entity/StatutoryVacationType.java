package com.peoplecore.vacation.entity;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/* 시스템 예약 휴가 유형 enum - 회사 생성 시 자동 INSERT 대상 */
/* 기존 월차/연차 + 근로기준법·남녀고용평등법·예비군법 등 법정 근로 휴가 단일 관리 */
/* 법 개정 시 이 파일만 수정 → initDefault() 재실행 시 누락 유형 자동 보충 */
public enum StatutoryVacationType {

    /* 월차 - 근기법 §60② (1년 미만 근로자 월 1일 유급) */
    MONTHLY       ("MONTHLY",        "월차",           "1.00",  1),
    /* 연차 - 근기법 §60 (1년 80% 이상 출근 시 15일, 근속 2년당 +1, 최대 25일) */
    ANNUAL        ("ANNUAL",         "연차",           "0.25",  2),
    /* 출산전후휴가 - 근기법 §74 (90일, 다태아 120일 / 출산 후 45일 이상 확보) */
    MATERNITY     ("MATERNITY",      "출산전후휴가",   "1.00", 10),
    /* 유산·사산휴가 - 근기법 §74⑦ (임신주수별 5~90일) */
    MISCARRIAGE   ("MISCARRIAGE",    "유산사산휴가",   "1.00", 11),
    /* 배우자 출산휴가 - 남녀고용평등법 §18-2 (2025.2.23 개정: 20일 유급) */
    SPOUSE_BIRTH  ("SPOUSE_BIRTH",   "배우자출산휴가", "1.00", 12),
    /* 난임치료휴가 - 남녀고용평등법 §18-3 (연 6일 / 최초 2일 유급, 2025 개정) */
    INFERTILITY   ("INFERTILITY",    "난임치료휴가",   "1.00", 13),
    /* 가족돌봄휴가 - 남녀고용평등법 §22-2 (연 10일, 최대 20일 무급) */
    FAMILY_CARE   ("FAMILY_CARE",    "가족돌봄휴가",   "1.00", 14),
    /* 생리휴가 - 근기법 §73 (월 1일 무급, 청구 시) */
    MENSTRUAL     ("MENSTRUAL",      "생리휴가",       "1.00", 15),
    /* 예비군훈련 - 예비군법 §10 (직장 불이익 처우 금지 / 공가 처리) */
    RESERVE_FORCES("RESERVE_FORCES", "예비군훈련",     "1.00", 20),
    /* 민방위훈련 - 민방위기본법 §27 */
    CIVIL_DEFENSE ("CIVIL_DEFENSE",  "민방위훈련",     "1.00", 21),
    /* 공민권 행사 - 근기법 §10 (선거·투표·법원 출석 등) */
    CIVIC_DUTY    ("CIVIC_DUTY",     "공민권행사",     "1.00", 22);

    /* VacationType.typeCode 에 그대로 저장되는 식별 코드 (UNIQUE per 회사) */
    private final String code;
    /* VacationType.typeName 에 저장되는 화면 표시명 */
    private final String name;
    /* 1회 신청 최소 단위 - 1.00=종일, 0.50=반차, 0.25=반반차 */
    private final BigDecimal deductUnit;
    /* 화면 정렬 순서 - 예약 유형은 커스텀 유형보다 앞쪽 번호 부여 */
    private final int sortOrder;

    /* 예약 코드 집합 - 관리자 create() 요청 시 시스템 예약 차단 판정용 */
    /* values() 순회 비용 제거 위해 enum 로딩 시 1회 계산한 불변 Set 캐시 */
    private static final Set<String> RESERVED_CODES =
            Arrays.stream(values())
                    .map(StatutoryVacationType::getCode)
                    .collect(Collectors.toUnmodifiableSet());

    StatutoryVacationType(String code, String name, String deductUnit, int sortOrder) {
        this.code = code;
        this.name = name;
        this.deductUnit = new BigDecimal(deductUnit); // 문자열 생성자 - double 오차 방지
        this.sortOrder = sortOrder;
    }

    public String getCode()           { return code; }
    public String getName()           { return name; }
    public BigDecimal getDeductUnit() { return deductUnit; }
    public int getSortOrder()         { return sortOrder; }

    /* 시스템 예약 코드 여부 - VacationTypeService.create() 에서 관리자 입력 코드 차단 시 사용 */
    /* 반환 true 면 VACATION_TYPE_SYSTEM_RESERVED 예외 발생시켜야 함 */
    public static boolean isReserved(String typeCode) {
        return typeCode != null && RESERVED_CODES.contains(typeCode);
    }

    /* 회사 생성 시 INSERT 용 VacationType 엔티티 변환 - 활성 상태로 생성 */
    public VacationType toEntity(UUID companyId) {
        return VacationType.builder()
                .companyId(companyId)
                .typeCode(code)
                .typeName(name)
                .deductUnit(deductUnit)
                .isActive(true)
                .sortOrder(sortOrder)
                .build();
    }
}
