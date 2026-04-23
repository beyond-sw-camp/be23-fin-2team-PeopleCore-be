package com.peoplecore.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * 휴가 결재 문서 생성 이벤트 collab -> hr.
 * hr 가 이 이벤트로 VacationReq N건 insert (PENDING). items 1건당 row 1건.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class VacationApprovalDocCreatedEvent {

    private UUID companyId;

    /* collab ApprovalDocument PK - hr 쪽에서 그룹핑 키로 사용 */
    private Long approvalDocId;

    /* 기안자 사원 ID */
    private Long empId;

    /* 기안자 이름/부서/직급/직책 스냅샷 (VacationReq 스냅샷 컬럼용) */
    private String empName;
    private Long deptId;
    private String deptName;
    private String empGrade;
    private String empTitle;

    /* 휴가 유형 ID (VacationInfo) */
    private Long infoId;

    /* 휴가 사유 (그룹 공통) */
    private String vacReqReason;

    /* 휴가 슬롯 배열 - 진실의 원천. N ≥ 1 보장 (hr consumer 에서 검증, 비어있으면 VACATION_REQ_ITEMS_EMPTY) */
    private List<VacationSlotItem> items;

    /* 최종 결재자 ID (참고용, 알림 필요 시 확장) */
    private Long finalApproverEmpId;
}
