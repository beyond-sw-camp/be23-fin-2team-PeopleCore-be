package com.peoplecore.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeveranceApprovalDocCreatedEvent {

    private UUID companyId;
    private Long approvalDocId;
    private Long sevId;     // 또는 retirementId 등
    private Long drafterId;
    private Long finalApproverEmpId;


//    private UUID companyId;
//    private Long sevId;
//    private Long empId;
//    private Long drafterId;
//    private Long formId;
//    private String formCode;          // "RETIREMENT_RESOLUTION"
//    private String htmlContent;       // dataMap 주입 + 사용자 수정 반영 HTML
//    private List<ApprovalLineDto> approvalLine;
}
