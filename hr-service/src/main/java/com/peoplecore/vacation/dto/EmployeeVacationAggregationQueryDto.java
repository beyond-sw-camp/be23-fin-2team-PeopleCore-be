package com.peoplecore.vacation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

/* 사원 단위 휴가 집계 projection - 부서 요약 카드 / 사원 상세 테이블 공용 */
/* QueryDSL Projections.constructor 로 생성. 모든 값은 해당 연도(balanceYear) 기준 */
/* 법정/특별 구분은 VacationType.typeCode 가 StatutoryVacationType enum 에 속하는지로 판정 */
/* null 방어: projection 쿼리에서 COALESCE(..., 0) 로 0 을 채워 전달 (호출부 추가 처리 불필요) */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeVacationAggregationQueryDto {

    /* 사원 ID */
    private Long empId;

    /* 소속 부서 ID - 부서별 그룹핑·사원 필터 용도 */
    private Long deptId;

    /* 법정 유형 available 합 (total - used - pending - expired) */
    private BigDecimal statutoryAvailable;

    /* 특별 유형 available 합 (회사 커스텀 유형) */
    private BigDecimal specialAvailable;

    /* 전체 유형 used 합 */
    private BigDecimal usedDays;

    /* 전체 유형 total 합 - 총연차 */
    private BigDecimal totalDays;

    /* 소진율(%) = used / total × 100. total 0 이면 0 반환 (0 으로 나누기 방지) */
    public int usageRatePercent() {
        if (totalDays.signum() <= 0) return 0;
        return usedDays.multiply(BigDecimal.valueOf(100))
                .divide(totalDays, 0, RoundingMode.HALF_UP)
                .intValue();
    }
}
