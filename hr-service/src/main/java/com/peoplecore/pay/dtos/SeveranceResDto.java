package com.peoplecore.pay.dtos;

import com.peoplecore.pay.domain.SeverancePays;
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
public class SeveranceResDto {
//    신규 - 목록

    private Long sevId;
    private Long empId;
    private String empName;
    private String deptName;
    private String gradeName;
    private String workGroupName;
    private String retirementType;
    private LocalDate hireDate;
    private LocalDate resignDate;
    private BigDecimal serviceYears;
    private Long severanceAmount;           // 퇴직금 산정액
    private Long taxAmount;                 // 퇴직소득세
    private Long netAmount;                 // 실지급액
    private Long dcDepositedTotal;          // DC 기적립 합계
    private Long dcDiffAmount;              // DC 차액
    private String sevStatus;               //퇴직금 상태
    private LocalDate transferDate;         //실지급 일자


    public static SeveranceResDto fromEntity(SeverancePays s) {
        return SeveranceResDto.builder()
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
                .severanceAmount(s.getSeveranceAmount())
                .taxAmount(s.getTaxAmount())
                .netAmount(s.getNetAmount())
                .dcDepositedTotal(s.getDcDepositedTotal())
                .dcDiffAmount(s.getDcDiffAmount())
                .sevStatus(s.getSevStatus().name())
                .transferDate(s.getTransferDate())
                .build();
    }
}
