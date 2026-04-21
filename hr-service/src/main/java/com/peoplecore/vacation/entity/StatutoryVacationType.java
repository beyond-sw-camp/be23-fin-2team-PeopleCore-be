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

    /* 월차 - 근기법  (1년 미만 근로자 월 1일 유급, 매월 스케줄러 적립) */
    MONTHLY       ("MONTHLY",        "월차",           "1.00",  1,
                   GrantMode.SCHEDULED,   GenderLimit.ALL,         PayType.PAID,   null),
    /* 연차 - 근기법 80% 이상 출근 시 15일, 근속 2년당 +1, 최대 25일) */
    ANNUAL        ("ANNUAL",         "연차",           "0.25",  2,
                   GrantMode.SCHEDULED,   GenderLimit.ALL,         PayType.PAID,   null),
    /* 출산전후휴가 - 근기법  (90일, 다태아 120일 / 출산 후 45일 이상 확보) */
    MATERNITY     ("MATERNITY",      "출산전후휴가",   "1.00", 10,
                   GrantMode.EVENT_BASED, GenderLimit.FEMALE_ONLY, PayType.PAID,   90),
    /* 유산·사산휴가 - 근기법  (임신주수별 5~90일, 신청 시 주수 입력) */
    MISCARRIAGE   ("MISCARRIAGE",    "유산사산휴가",   "1.00", 11,
                   GrantMode.EVENT_BASED, GenderLimit.FEMALE_ONLY, PayType.PAID,   null),
    /* 배우자 출산휴가 - 남녀고용평등법  (2025.2.23 개정: 20일 유급) */
    SPOUSE_BIRTH  ("SPOUSE_BIRTH",   "배우자출산휴가", "1.00", 12,
                   GrantMode.EVENT_BASED, GenderLimit.MALE_ONLY,   PayType.PAID,   20),
    /* 가족돌봄휴가 - 남녀고용평등법 (연 10일 무급, 첫 신청 시 전체 적립) */
    FAMILY_CARE   ("FAMILY_CARE",    "가족돌봄휴가",   "1.00", 14,
                   GrantMode.EVENT_BASED, GenderLimit.ALL,         PayType.UNPAID, 10),
    /* 생리휴가 - 근기법  (월 1일 무급, 매월 스케줄러 적립 + 전월 미사용 만료) */
    MENSTRUAL     ("MENSTRUAL",      "생리휴가",       "1.00", 15,
                   GrantMode.SCHEDULED,   GenderLimit.FEMALE_ONLY, PayType.UNPAID, 1),
    /* 공가 - 근기법 + 예비군법 + 민방위기본법 (증빙별 일수, 연 누적 row 1개) */
    OFFICIAL_LEAVE("OFFICIAL_LEAVE", "공가",           "1.00", 20,
                   GrantMode.EVENT_BASED, GenderLimit.ALL,         PayType.PAID,   null);

    /* VacationType.typeCode 에 그대로 저장되는 식별 코드 (UNIQUE per 회사) */
    private final String code;
    /* VacationType.typeName 에 저장되는 화면 표시명 */
    private final String name;
    /* 1회 신청 최소 단위 - 1.00=종일, 0.50=반차, 0.25=반반차 */
    private final BigDecimal deductUnit;
    /* 화면 정렬 순서 - 예약 유형은 커스텀 유형보다 앞쪽 번호 부여 */
    private final int sortOrder;
    /* 부여 방식 - SCHEDULED(스케줄러 자동) / EVENT_BASED(신청 시 생성) */
    private final GrantMode grantMode;
    /* 성별 제한 - 신청 시 사원 성별과 비교해 차단 여부 판정 */
    private final GenderLimit genderLimit;
    /* 유급/무급 - 급여 계산 시 차감 근거 */
    private final PayType payType;
    /* 기본 부여 일수 - 이벤트성 고정 일수(출산=90/배우자=20/가족돌봄=10)·스케줄러 월간(월차/생리=1) */
    /* null 인 경우: 연차(정책 규칙 기반), 유산사산·공가(신청 시 사용자 입력) */
    private final Integer defaultDays;

    /* 예약 코드 집합 - 관리자 create() 요청 시 시스템 예약 차단 판정용 */
    /* values() 순회 비용 제거 위해 enum 로딩 시 1회 계산한 불변 Set 캐시 */
    private static final Set<String> RESERVED_CODES =
            Arrays.stream(values())
                    .map(StatutoryVacationType::getCode)
                    .collect(Collectors.toUnmodifiableSet());

    StatutoryVacationType(String code, String name, String deductUnit, int sortOrder,
                          GrantMode grantMode, GenderLimit genderLimit, PayType payType,
                          Integer defaultDays) {
        this.code = code;
        this.name = name;
        this.deductUnit = new BigDecimal(deductUnit); // 문자열 생성자 - double 오차 방지
        this.sortOrder = sortOrder;
        this.grantMode = grantMode;
        this.genderLimit = genderLimit;
        this.payType = payType;
        this.defaultDays = defaultDays;
    }

    public String getCode()           { return code; }
    public String getName()           { return name; }
    public BigDecimal getDeductUnit() { return deductUnit; }
    public int getSortOrder()         { return sortOrder; }
    public GrantMode getGrantMode()   { return grantMode; }
    public GenderLimit getGenderLimit() { return genderLimit; }
    public PayType getPayType()       { return payType; }
    public Integer getDefaultDays()   { return defaultDays; }

    /* 시스템 예약 코드 여부 - VacationTypeService.create() 에서 관리자 입력 코드 차단 시 사용 */
    /* 반환 true 면 VACATION_TYPE_SYSTEM_RESERVED 예외 발생시켜야 함 */
    public static boolean isReserved(String typeCode) {
        return typeCode != null && RESERVED_CODES.contains(typeCode);
    }

    /* typeCode → enum 매핑. 시스템 예약이 아니면 null (일반 커스텀 유형) */
    /* VacationRequestService 가 grantMode 로 분기할 때 사용 */
    public static StatutoryVacationType fromCode(String typeCode) {
        if (typeCode == null) return null;
        for (StatutoryVacationType t : values()) {
            if (t.code.equals(typeCode)) return t;
        }
        return null;
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
                .genderLimit(genderLimit)
                .payType(payType)
                .build();
    }
}
