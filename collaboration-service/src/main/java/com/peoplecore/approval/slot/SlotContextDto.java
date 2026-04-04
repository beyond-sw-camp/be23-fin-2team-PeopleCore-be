package com.peoplecore.approval.slot;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SlotContextDto {
    private final String companyName;
    private final String deptCode;
    private final String deptName;
    private final String formCode;
    private final String formName;
}
