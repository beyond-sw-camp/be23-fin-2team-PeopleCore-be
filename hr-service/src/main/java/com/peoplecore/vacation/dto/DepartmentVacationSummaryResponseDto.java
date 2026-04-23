package com.peoplecore.vacation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/* 전사 휴가 관리 - 부서별 요약 카드 DTO */
/* 부서 이름, 부서원 수, 평균 소진율, 소진율 낮은 N명, 총/사용/잔여 일수 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepartmentVacationSummaryResponseDto {

    /* 부서 ID - 사원 상세 호출 시 path param */
    private Long deptId;

    /* 부서명 - 화면 카드 상단 표시 */
    private String deptName;

    /* 부서원 수 - 재직(ACTIVE) 기준 */
    private Integer memberCount;

    /* 평균 소진율(%) - 사원별 소진율의 산술평균. 소수 0자리 반올림 */
    private Integer avgUsageRate;

    /* 소진율 낮은 사원 수 - lowUsageThreshold(%) 미만. 기본 30% */
    private Integer lowUsageCount;

    /* 총 일수 합 - 부서 전체 total_days 합 (법정+특별) */
    private BigDecimal totalDays;

    /* 사용 일수 합 */
    private BigDecimal usedDays;

    /* 잔여 일수 합 (법정 + 특별 available 합) */
    private BigDecimal availableDays;
}
