package com.peoplecore.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 휴가 결재 문서 생성 이벤트 collab -> hr.
 * hr 가 이 이벤트로 VacationReq insert (PENDING).
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class VacationApprovalDocCreatedEvent {

    private UUID companyId;

    /* collab ApprovalDocument PK */
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

    /* 휴가 시작/종료 일시 */
    private LocalDateTime vacReqStartat;
    private LocalDateTime vacReqEndat;

    /* 사용일수 (종일 1.0 / 반차 0.5 / 반반차 0.25 / 다일 N.0) */
    private BigDecimal vacReqUseDay;

    /* 휴가 사유 */
    private String vacReqReason;

    /* 최종 결재자 ID (참고용, 알림 필요 시 확장) */
    private Long finalApproverEmpId;
}
