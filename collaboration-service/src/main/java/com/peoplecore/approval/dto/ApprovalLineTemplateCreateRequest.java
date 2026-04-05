package com.peoplecore.approval.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApprovalLineTemplateCreateRequest {
    private String lineTemName;
    private Boolean isDefault;
    private List<ApprovalLineTemplateListItemDto> itemDto;
}
