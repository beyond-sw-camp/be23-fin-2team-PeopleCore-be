package com.peoplecore.vacation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/* 휴가 부여 신청 모달용 - 신청 가능한 법정 휴가 유형 + 현재 잔여 + 한도 + 추가 신청 가능 일수 */
/* EVENT_BASED 유형(출산/유산/배우자출산/가족돌봄/공가) + 본인 성별에 맞는 유형만 반환 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationGrantableTypeResponse {

    /* 휴가 유형 ID - 부여 신청 docData 의 infoId 로 사용 */
    private Long typeId;

    /* 유형 코드 - MATERNITY/MISCARRIAGE/SPOUSE_BIRTH/FAMILY_CARE/OFFICIAL_LEAVE */
    private String typeCode;

    /* 유형 표시명 */
    private String typeName;

    /* 법정 연간 한도 - MISCARRIAGE/OFFICIAL_LEAVE 는 null(한도 없음) */
    private BigDecimal cap;

    /* 기준 연도 (올해) */
    private Integer balanceYear;

    /* 올해 누적 부여 일수 - Balance 없으면 0 */
    private BigDecimal totalDays;

    /* 사용한 일수 */
    private BigDecimal usedDays;

    /* 사용 신청(USE) 결재 대기 일수 - Balance.pendingDays */
    private BigDecimal pendingUseDays;

    /* 부여 신청(GRANT) 결재 대기 일수 - 아직 승인 안 난 추가 신청분 */
    private BigDecimal pendingGrantDays;

    /* 본인이 소유(사용 가능)한 일수 = total - used - pendingUse - expired */
    private BigDecimal availableDays;

    /* 남은 신청 가능 일수 = cap - total - pendingGrant (cap null 이면 null = 무제한) */
    /* 음수 방지: cap 초과 상태면 0 으로 클램프 */
    private BigDecimal grantableDays;
}
