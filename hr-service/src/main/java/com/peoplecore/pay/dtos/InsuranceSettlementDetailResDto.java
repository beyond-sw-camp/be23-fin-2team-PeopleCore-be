package com.peoplecore.pay.dtos;

import com.peoplecore.pay.domain.InsuranceSettlement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InsuranceSettlementDetailResDto {

    private Long settlementId;
    private String payYearMonth;
    private String settlementFromMonth;
    private String settlementToMonth;
    private Long empId;
    private String empName;
    private String deptName;
    private String gradeName;
    private String titleName;
    private Long baseSalary;

//    요율정보(표시용)
    private BigDecimal pensionRate;
    private BigDecimal healthRate;
    private BigDecimal ltcRate;
    private BigDecimal employmentRate;
    private BigDecimal employmentEmployerRate;
    private BigDecimal industrialRate;

//    정산액
    private Long pensionEmployee;
    private Long pensionEmployer;
    private Long healthEmployee;
    private Long healthEmployer;
    private Long ltcEmployee;
    private Long ltcEmployer;
    private Long employmentEmployee;
    private Long employmentEmployer;
    private Long industrialEmployer;
    private Long totalEmployee;
    private Long totalEmployer;
    private Long totalAmount;

//    기공제액
    private Long deductedPension;
    private Long deductedHealth;
    private Long deductedLtc;
    private Long deductedEmployment;
    private Long totalDeducted;

//    차액
    private Long diffPension;
    private Long diffHealth;
    private Long diffLtc;
    private Long diffEmployment;
    private Long totalDiff;


    private Boolean isApplied;



    public static InsuranceSettlementDetailResDto fromEntity(InsuranceSettlement s){
        return InsuranceSettlementDetailResDto.builder()
                .settlementId(s.getSettlementId())
                .payYearMonth(s.getPayYearMonth())
                .settlementFromMonth(s.getSettlementFromMonth())
                .settlementToMonth(s.getSettlementToMonth())
                .empId(s.getEmployee().getEmpId())
                .empName(s.getEmployee().getEmpName())
                .deptName(s.getEmployee().getDept().getDeptName())
                .gradeName(s.getEmployee().getGrade() != null ? s.getEmployee().getGrade().getGradeName() : null)
                .titleName(s.getEmployee().getTitle() != null ? s.getEmployee().getTitle().getTitleName() : null)
                .baseSalary(s.getBaseSalary())
//                요율
                .pensionRate(s.getInsuranceRates().getNationalPension())
                .healthRate(s.getInsuranceRates().getHealthInsurance())
                .ltcRate(s.getInsuranceRates().getLongTermCare())
                .employmentRate(s.getInsuranceRates().getEmploymentInsurance())
                .employmentEmployerRate(s.getInsuranceRates().getEmploymentInsuranceEmployer())
                .industrialRate(s.getInsuranceRates().getIndustrialAccident())
                // 정산액
                .pensionEmployee(s.getPensionEmployee())
                .pensionEmployer(s.getPensionEmployer())
                .healthEmployee(s.getHealthEmployee())
                .healthEmployer(s.getHealthEmployer())
                .ltcEmployee(s.getLtcEmployee())
                .ltcEmployer(s.getLtcEmployer())
                .employmentEmployee(s.getEmploymentEmployee())
                .employmentEmployer(s.getEmploymentEmployer())
                .industrialEmployer(s.getIndustrialEmployer())
                .totalEmployee(s.getTotalEmployee())
                .totalEmployer(s.getTotalEmployer())
                .totalAmount(s.getTotalAmount())
                // 기공제액
                .deductedPension(s.getDeductedPension())
                .deductedHealth(s.getDeductedHealth())
                .deductedLtc(s.getDeductedLtc())
                .deductedEmployment(s.getDeductedEmployment())
                .totalDeducted(s.getTotalDeducted())
                // 차액
                .diffPension(s.getDiffPension())
                .diffHealth(s.getDiffHealth())
                .diffLtc(s.getDiffLtc())
                .diffEmployment(s.getDiffEmployment())
                .totalDiff(s.getTotalDiff())
                .isApplied(s.getIsApplied())
                .build();
    }

}
