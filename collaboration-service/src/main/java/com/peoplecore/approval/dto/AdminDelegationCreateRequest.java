package com.peoplecore.approval.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminDelegationCreateRequest {

    // 위임자(원래 결재자) 정보
    private Long empId;
    private String empName;
    private String empDeptName;
    private String empGrade;
    private String empTitle;

    // 대결자 정보
    private Long appDeleEmpId;
    private String deleName;
    private String deleDeptName;
    private String deleGrade;
    private String deleTitle;

    // 위임 기간 + 사유
    private LocalDate appDeleStartAt;
    private LocalDate appDeleEndAt;
    private String appDeleReason;
}
