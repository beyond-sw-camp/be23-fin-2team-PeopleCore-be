package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InsuranceSettlementSummaryResDto {
//상단 요약 카드 (전체 합계)

    private String settlementFromMonth;
    private String settlementToMonth;
    private Integer totalEmployees;

    // 반영 현황
    private Integer appliedCount;           // 반영완료 인원수
    private Long totalChargeAmount;         // 추가징수 총액 (diff > 0 합산)
    private Long totalRefundAmount;         // 환급 총액 (diff < 0 합산, 절대값)

//    정산액 합산
    private Long totalBaseSalary;
    private Long totalPensionEmployee;
    private Long totalPensionEmployer;
    private Long totalHealthEmployee;
    private Long totalHealthEmployer;
    private Long totalLtcEmployee;
    private Long totalLtcEmployer;
    private Long totalEmploymentEmployee;
    private Long totalEmploymentEmployer;
    private Long totalIndustrialEmployer;
    private Long grandTotalEmployee;        //근로자 부담 합계
    private Long grandTotalEmployer;        //사업주 부담 합계

    // ── 기공제액 합산 ──
    private Long grandTotalDeducted;

    // ── 차액 합산 ──
    private Long grandTotalDiff;

//    사원별 목록(페이징)
    private List<InsuranceSettlementResDto> settlements;

}
