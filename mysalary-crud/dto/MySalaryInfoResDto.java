package com.peoplecore.pay.dto;

import com.peoplecore.employee.domain.Employee;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 내 급여 조회 - 급여 정보 + 사원 기본 정보 응답 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MySalaryInfoResDto {

    // ── 사원 기본 정보 ──
    private Long empId;
    private String empName;
    private String empEmail;
    private String empNum;
    private String empPhone;
    private String empType;
    private LocalDate empHireDate;
    private String deptName;
    private String gradeName;
    private String titleName;
    private String profileImageUrl;

    // ── 급여 상세 ──
    private BigDecimal annualSalary;      // 연봉
    private Long monthlySalary;           // 월급
    private List<FixedAllowanceDto> fixedAllowances;  // 고정수당 목록

    // ── 계좌 정보 ──
    private AccountDto salaryAccount;          // 급여 계좌
    private RetirementAccountDto retirementAccount;  // 퇴직연금 계좌

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FixedAllowanceDto {
        private Long payItemId;
        private String payItemName;
        private Integer amount;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountDto {
        private Long empAccountId;
        private String bankName;
        private String accountNumber;
        private String accountHolder;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetirementAccountDto {
        private Long retirementAccountId;
        private String retirementType;
        private String pensionProvider;
        private String accountNumber;
    }

    public static MySalaryInfoResDto fromEmployee(Employee emp) {
        return MySalaryInfoResDto.builder()
                .empId(emp.getEmpId())
                .empName(emp.getEmpName())
                .empEmail(emp.getEmpEmail())
                .empNum(emp.getEmpNum())
                .empPhone(emp.getEmpPhone())
                .empType(emp.getEmpType().name())
                .empHireDate(emp.getEmpHireDate())
                .deptName(emp.getDept() != null ? emp.getDept().getDeptName() : null)
                .gradeName(emp.getGrade() != null ? emp.getGrade().getGradeName() : null)
                .titleName(emp.getTitle() != null ? emp.getTitle().getTitleName() : null)
                .profileImageUrl(emp.getEmpProfileImageUrl())
                .build();
    }
}
