package com.peoplecore.pay.dtos;

import com.peoplecore.pay.domain.InsuranceSettlement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InsuranceSettlementResDto {
//    사원별 보험료 요약

    private Long settlementId;
    private Long empId;
    private String empName;
    private String deptName;
    private Long baseSalary;

//    정산액
    private Long pensionEmployee;
    private Long healthEmployee;
    private Long ltcEmployee;
    private Long employmentEmployee;
    private Long totalEmployee;

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

    // 차액 구분 (화면 표시용): "추가징수" / "환급" / "차액없음"
    private String diffCategory;

    private Boolean isApplied;


    public static InsuranceSettlementResDto fromEntity(InsuranceSettlement s){
        long totalDiff = s.getTotalDiff();
        String category;
        if (totalDiff > 0) {
            category = "추가징수";
        } else if (totalDiff < 0) {
            category = "환급";
        } else {
            category = "차액없음";
        }

        return InsuranceSettlementResDto.builder()
                .settlementId(s.getSettlementId())
                .empId(s.getEmployee().getEmpId())
                .empName(s.getEmployee().getEmpName())
                .deptName(s.getEmployee().getDept().getDeptName())
                .baseSalary(s.getBaseSalary())
                // 정산액
                .pensionEmployee(s.getPensionEmployee())
                .healthEmployee(s.getHealthEmployee())
                .ltcEmployee(s.getLtcEmployee())
                .employmentEmployee(s.getEmploymentEmployee())
                .totalEmployee(s.getTotalEmployee())
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
