package com.peoplecore.approval.dto;

import com.peoplecore.approval.entity.ApprovalDelegation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalDelegationResponse {

    private Long appDeleId;
    private Long empId;
    private String empName;
    private String empDeptName;
    private String empGrade;
    private String empTitle;
    private Long deleEmpId;
    private String deleName;
    private String deleDeptName;
    private String deleGrade;
    private String deleTitle;
    private LocalDate startAt;
    private LocalDate endAt;
    private String reason;
    private Boolean isActive;
    private LocalDateTime createdAt;

    public static ApprovalDelegationResponse from(ApprovalDelegation entity) {
        return ApprovalDelegationResponse.builder()
                .appDeleId(entity.getAppDeleId())
                .empId(entity.getEmpId())
                .empName(entity.getEmpName())
                .empDeptName(entity.getEmpDeptName())
                .empGrade(entity.getEmpGrade())
                .empTitle(entity.getEmpTitle())
                .deleEmpId(entity.getDeleEmpId())
                .deleName(entity.getDeleName())
                .deleDeptName(entity.getDeleDeptName())
                .deleGrade(entity.getDeleGrade())
                .deleTitle(entity.getDeleTitle())
                .startAt(entity.getStartAt())
                .endAt(entity.getEndAt())
                .reason(entity.getReason())
                .isActive(entity.getIsActive())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}