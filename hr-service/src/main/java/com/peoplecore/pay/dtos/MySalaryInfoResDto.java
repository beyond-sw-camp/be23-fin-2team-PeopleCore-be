package com.peoplecore.pay.dtos;

import com.peoplecore.employee.domain.EmpType;
import com.peoplecore.pay.enums.PensionType;
import com.peoplecore.pay.enums.RetirementType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MySalaryInfoResDto {
// 내급여정보

    private Long empId;
    private String empName;
    private String empEmail;
    private String empNum;
    private String empPhone;
    private EmpType empType;
    private LocalDate empHireDate;
    private String deptName;
    private String gradeName;
    private String titleName;
    private String profileImageUrl;
    private SalaryInfoDto  salaryInfo;

    private Integer dependentsCount;

    private AccountDto  salaryAccount;
    private RetirementAccountDto  retirementAccount;

    private PensionType companyPensionType;    // 회사 퇴직연금 설정 (severance/DB/DC/DB_DC)
    private RetirementType empRetirementType;     // 사원 퇴직연금 유형 (severance/DB/DC)

    // 회사 통합 운용사/계좌 (DB/DB_DC일 때만 의미 있음)
    private String companyPensionProvider;


    @Data @Builder @AllArgsConstructor @NoArgsConstructor
    public static class SalaryInfoDto {
        private Long annualSalary;
        private Long monthlySalary;
        private List<FixedAllowanceDto> fixedAllowances;
    }


        @Data @Builder @AllArgsConstructor @NoArgsConstructor
        public static class FixedAllowanceDto {
            private Long payItemId;
            private String payItemName;
            private Long amount;
        }

        @Data @Builder @AllArgsConstructor @NoArgsConstructor
        public static class AccountDto {
            private Long empAccountId;
            private String bankName;
            private String accountNumber;
            private String accountHolder;
        }

        @Data @Builder @AllArgsConstructor @NoArgsConstructor
        public static class RetirementAccountDto {
            private Long retirementAccountId;
            private String retirementType;
            private String pensionProvider;
            private String accountNumber;
        }
}
