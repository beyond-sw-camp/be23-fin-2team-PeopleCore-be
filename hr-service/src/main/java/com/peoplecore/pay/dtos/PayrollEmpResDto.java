package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollEmpResDto {
//    급여대장 사원 행

    private Long empId;
    private String empNum;
    private String empName;
    private String deptName;
    private String gradeName;
    private String empType;
    private String status;

    private Long totalPay;          //지급합계
    private Long totalDeduction;    //공제합계
    private Long netPay;            //공제후 금액
    private Long unpaid;            //미지급액

    private String payrollEmpStatus;   // CALCULATING / CONFIRMED 산정중/확정
    private String empStatus;          // ACTIVE / ON_LEAVE / RESIGNED 재직/휴직/퇴직
    private Long approvalDocId;

    private Long pendingOvertimeAmount;   // null = OT 결재 없음, 0 = 적용완료, > 0 = 미적용 금액

    /* 일할계산 정보 - 그 달 일부만 재직한 경우(입사 또는 퇴직) */
    private Boolean isProrated;
    private Integer proratedDays;        // 실제 재직 일수
    private Integer monthDays;           // 그 달 총 일수
    private LocalDate effectiveResignDate;  // 퇴직(예정)일 — 그 달에만
    private LocalDate effectiveHireDate;    // 신규 입사일 — 그 달에만


}
