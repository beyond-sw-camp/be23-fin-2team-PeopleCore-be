package com.peoplecore.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeveranceApprovalDocCreatedEvent {

    private UUID companyId;
    private Long approvalDocId;     // collab 이 만든 결재 문서 ID
    private List<Long> sevIds;
    private Long drafterId;
    private Long finalApproverEmpId;
    private String htmlContent;       // 결재 상신 시점의 완성된 HTML (스냅샷용)


//    private UUID companyId;
//    private Long sevId;
//    private Long empId;
//    private Long drafterId;
//    private Long formId;
//    private String formCode;          // "RETIREMENT_RESOLUTION"
//    private List<ApprovalLineDto> approvalLine;
}
