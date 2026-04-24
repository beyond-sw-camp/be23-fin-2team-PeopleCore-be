package com.peoplecore.pay.dtos;

import com.peoplecore.employee.domain.EmpType;
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
    private AccountDto  salaryAccount;
    private RetirementAccountDto  retirementAccount;


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
