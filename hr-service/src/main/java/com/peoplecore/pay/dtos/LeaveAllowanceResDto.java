package com.peoplecore.pay.dtos;

import com.peoplecore.pay.domain.LeaveAllowance;
import com.peoplecore.pay.enums.AllowanceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveAllowanceResDto {
//    사원별 연차수당 행

    private Long allowanceId;
    private Long empId;
    private String empName;
    private String deptName;
    private String gradeName;
    private LocalDate hireDate;
    private LocalDate resignDate;           // 퇴직자용 (nullable)


    private Long normalMonthlySalary;       // 통상임금(월)
    private Long dailyWage;                 // 일 통상임금

    private BigDecimal totalLeaveDays;      // 부여일수
    private BigDecimal usedLeaveDays;       // 사용일수
    private BigDecimal unusedLeaveDays;     // 미사용일수 (수정 가능)

    private Long allowanceAmount;           // 산정금액
    private AllowanceStatus status;         // PENDING / CALCULATED / APPLIED
    private String appliedMonth;            // 반영월


    public static LeaveAllowanceResDto fromEntity(LeaveAllowance la){
        return LeaveAllowanceResDto.builder()
                .allowanceId(la.getAllowanceId())
                .empId(la.getEmployee().getEmpId())
                .empName(la.getEmployee().getEmpName())
                .deptName(la.getEmployee().getDept().getDeptName())
                .gradeName(la.getEmployee().getGrade().getGradeName())
                .hireDate(la.getEmployee().getEmpHireDate())
                .resignDate(la.getResignDate())
                .normalMonthlySalary(la.getNormalMonthlySalary())
                .dailyWage(la.getDailyWage())
                .totalLeaveDays(la.getTotalLeaveDays())
                .usedLeaveDays(la.getUsedLeaveDays())
                .unusedLeaveDays(la.getUnusedLeaveDays())
                .allowanceAmount(la.getAllowanceAmount())
                .status(la.getStatus())
                .appliedMonth(la.getAppliedMonth())
                .build();

    }
}
