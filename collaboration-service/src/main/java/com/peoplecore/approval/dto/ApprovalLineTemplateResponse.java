package com.peoplecore.approval.dto;

import com.peoplecore.approval.entity.ApprovalLineTemplate;
import com.peoplecore.approval.entity.ApprovalLineTemplateList;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalLineTemplateResponse {
    private Long lineTemId;
    private String lineTemName;
    private Boolean isDefault;
    private List<ApprovalLineTemplateListItemDto> itemDto;

    public static ApprovalLineTemplateResponse from(ApprovalLineTemplate template, List<ApprovalLineTemplateList> item) {
        return ApprovalLineTemplateResponse.builder()
                .lineTemId(template.getLineTemId())
                .lineTemName(template.getLineTemName())
                .isDefault(template.getIsDefault())
                .itemDto(item.stream()
                        .map(items -> ApprovalLineTemplateListItemDto.builder()
                                .empId(items.getLineTemListEmpId())
                                .approvalRole(items.getApprovalRole())
                                .step(items.getLineTemListStep())
                                .build())
                        .toList())
                .build();

    }
}
