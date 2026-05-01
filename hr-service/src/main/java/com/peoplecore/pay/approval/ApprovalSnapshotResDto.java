package com.peoplecore.pay.approval;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Builder
@Getter
public class ApprovalSnapshotResDto {
    private Long approvalDocId;
    private ApprovalFormType approvalType;
    private String htmlSnapshot;
    private LocalDateTime createdAt;
}
