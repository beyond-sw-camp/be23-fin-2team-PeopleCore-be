package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PensionDepositByEmployeeSummaryResDto {
//    사원 집계 요청
    private Integer totalEmployees;
    private Long totalDepositAmount;
    private Long monthlyAverage;
    private Long grandTotalDeposited;
    // ── 적립예정(자동 산정 후 미처리) 집계 — 운영자 행동 유도용 ──
    private Integer scheduledCount;     // 적립예정 사원 수 (DISTINCT empId)
    private Long scheduledAmount;       // 적립예정 총 금액
    private List<String> scheduledMonths;  // 처리 대기 payYearMonth 목록 (오름차순)

    private List<PensionDepositByEmployeeResDto> employees;

}
