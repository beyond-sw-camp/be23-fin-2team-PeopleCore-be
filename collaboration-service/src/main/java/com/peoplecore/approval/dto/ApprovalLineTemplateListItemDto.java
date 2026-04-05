package com.peoplecore.approval.dto;

import com.peoplecore.approval.entity.ApprovalRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalLineTemplateListItemDto {
    private Long empId;
    private ApprovalRole approvalRole;
    private Integer step;
}
