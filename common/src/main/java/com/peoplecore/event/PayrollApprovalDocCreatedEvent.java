package com.peoplecore.event;

import com.peoplecore.dtos.ApprovalLineDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollApprovalDocCreatedEvent {
    private UUID companyId;
    private Long payrollRunId;
    private Long drafterId;
    private String formCode;          // "PAYROLL_PAYMENT"
    private String htmlContent;       // dataMap 주입 + 사용자 수정 반영 HTML
    private List<ApprovalLineDto> approvalLine;
}
