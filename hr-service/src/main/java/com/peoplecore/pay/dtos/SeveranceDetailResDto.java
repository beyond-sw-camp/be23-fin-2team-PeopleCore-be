package com.peoplecore.pay.dtos;

import com.peoplecore.pay.domain.SeverancePays;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeveranceDetailResDto {
// 신규 - 상세

    /* ── 사원 정보 ── */
    private Long sevId;
    private Long empId;
    private String empName;
    private String deptName;
    private String gradeName;
    private String workGroupName;
    private String retirementType;
    private LocalDate hireDate;
    private LocalDate resignDate;

    /* ── 근속 정보 ── */
    private BigDecimal serviceYears;
    private Long serviceDays;

    /* ── 산정 기초 ── */
    private Long last3MonthPay;
    private Long lastYearBonus;
    private Long annualLeaveAllowance;
    private Integer last3MonthDays;
    private BigDecimal avgDailyWage;

    /* ── 산정 금액 ── */
    private Long severanceAmount;
    private Long taxAmount;
    private Long netAmount;

    /* ── DC형 정보 ── */
    private Long dcDepositedTotal;
    private Long dcDiffAmount;

    /* ── 상태/처리 정보 ── */
    private String sevStatus;
    private Long approvalDocId;
    private LocalDate transferDate;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
    private Long paidBy;
    private LocalDateTime paidAt;

    public static SeveranceDetailResDto fromEntity(SeverancePays s) {
        return SeveranceDetailResDto.builder()
                .sevId(s.getSevId())
                .empId(s.getEmployee().getEmpId())
                .empName(s.getEmpName())
                .deptName(s.getDeptName())
                .gradeName(s.getGradeName())
                .workGroupName(s.getWorkGroupName())
                .retirementType(s.getRetirementType().name())
                .hireDate(s.getHireDate())
                .resignDate(s.getResignDate())
                .serviceYears(s.getServiceYears())
                .serviceDays(s.getServiceDays())
                .last3MonthPay(s.getLast3MonthPay())
                .lastYearBonus(s.getLastYearBonus())
                .annualLeaveAllowance(s.getAnnualLeaveAllowance())
                .last3MonthDays(s.getLast3MonthDays())
                .avgDailyWage(s.getAvgDailyWage())
                .severanceAmount(s.getSeveranceAmount())
                .taxAmount(s.getTaxAmount())
                .netAmount(s.getNetAmount())
                .dcDepositedTotal(s.getDcDepositedTotal())
                .dcDiffAmount(s.getDcDiffAmount())
                .sevStatus(s.getSevStatus().name())
                .approvalDocId(s.getApprovalDocId())
                .transferDate(s.getTransferDate())
                .confirmedBy(s.getConfirmedBy())
                .confirmedAt(s.getConfirmedAt())
                .paidBy(s.getPaidBy())
                .paidAt(s.getPaidAt())
                .build();
    }
}
