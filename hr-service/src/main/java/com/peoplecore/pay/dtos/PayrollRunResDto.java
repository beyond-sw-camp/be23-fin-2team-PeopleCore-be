package com.peoplecore.pay.dtos;

import com.peoplecore.pay.domain.PayrollRuns;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollRunResDto {
    //급여대장 전체조회 응답(상단요약 카드 + 사원별 목록)

//    급여대장 요약(상단 카드)
    private Long payrollRunId;
    private String payYearMonth;
    private String payrollStatus;
    private Integer totalEmployees;
    private Long totalPay;
    private Long totalDeduction;
    private Long totalNetPay;
    private Long unpaidAmount;
    private LocalDate payDate;

//    사원별 목록
    private List<PayrollEmpResDto> employees;


    public static PayrollRunResDto fromEntity(PayrollRuns run, List<PayrollEmpResDto> employees){
        Long unpaid = employees == null ? 0L
                : employees.stream()
                  .mapToLong(e -> e.getUnpaid() != null ? e.getUnpaid() : 0L)
                  .sum();

        return PayrollRunResDto.builder()
                .payrollRunId(run.getPayrollRunId())
                .payYearMonth(run.getPayYearMonth())
                .payrollStatus(run.getPayrollStatus().name())
                .totalEmployees(run.getTotalEmployees())
                .totalPay(run.getTotalPay())
                .totalDeduction(run.getTotalDeduction())
                .totalNetPay(run.getTotalNetPay())
                .unpaidAmount(unpaid)
                .payDate(run.getPayDate())
                .employees(employees)
                .build() ;
    }

}
