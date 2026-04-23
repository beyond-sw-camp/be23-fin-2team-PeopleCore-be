package com.peoplecore.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalLineDto {
    private Long approverId;     // 결재자 사원 ID
    private Integer order;       // 결재 순서 (1, 2, 3, ...)
    private String approvalType; // "APPROVE" / "REVIEW" / "AGREEMENT" 등 (collab 정의 따름)
}
