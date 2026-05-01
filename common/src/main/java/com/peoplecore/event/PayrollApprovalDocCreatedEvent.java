package com.peoplecore.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollApprovalDocCreatedEvent {

    private UUID companyId;
    private Long approvalDocId;     // collab 이 만든 결재 문서 ID
    private Long payrollRunId;      // hr 매칭 키
    private Long drafterId;
    private Long finalApproverEmpId;  // 최종 결재자
    private String htmlContent;       // 결재 상신 시점의 완성된 HTML (스냅샷용)

//    private UUID companyId;
//    private Long payrollRunId;
//    private Long drafterId;
//    private Long formId;
//    private String formCode;          // "PAYROLL_RESOLUTION"
//    private List<ApprovalLineDto> approvalLine;
//    private String hrRefType;
//    private Long hrRefId;

}
