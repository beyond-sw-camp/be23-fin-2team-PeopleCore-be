package com.peoplecore.vacation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/* QueryDSL Projection 전용 - listGrantableTypes 단일 쿼리 결과 담음 */
/* cap / grantableDays 는 StatutoryVacationType enum 에서 산출 → Service 후처리 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GrantableTypeQueryDto {

    /* 휴가 유형 ID */
    private Long typeId;

    /* 유형 코드 */
    private String typeCode;

    /* 유형 표시명 */
    private String typeName;

    /* 올해 누적 부여 일수 - Balance 없으면 0 (COALESCE) */
    private BigDecimal totalDays;

    /* 사용한 일수 */
    private BigDecimal usedDays;

    /* 사용 신청(USE) 결재 대기 일수 - Balance.pendingDays */
    private BigDecimal pendingUseDays;

    /* 사용 가능 잔여 = total - used - pendingUse - expired */
    private BigDecimal availableDays;

    /* 부여 신청(GRANT) PENDING 합 - 상관 서브쿼리로 산출 */
    private BigDecimal pendingGrantDays;
}
