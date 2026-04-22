package com.peoplecore.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 휴가 부여 신청 결재 문서 생성 이벤트 collab → hr.
 * hr 가 이 이벤트로 VacationGrantRequest insert (PENDING).
 * USE 이벤트(VacationApprovalDocCreatedEvent) 와 분리된 별도 토픽 (vacation-grant-approval-doc-created).
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class VacationGrantApprovalDocCreatedEvent {

    private UUID companyId;

    /* collab ApprovalDocument PK */
    private Long approvalDocId;

    /* 기안자 사원 ID */
    private Long empId;

    /* 기안자 이름/부서/직급/직책 스냅샷 (VacationGrantRequest 스냅샷 컬럼용) */
    private String empName;
    private Long deptId;
    private String deptName;
    private String empGrade;
    private String empTitle;

    /* 휴가 유형 ID (VacationType) - 법정 휴가 유형(MATERNITY/MISCARRIAGE/...) 기대 */
    private Long infoId;

    /* 부여 요청 일수 (MISCARRIAGE 는 서버에서 주수 기반으로 자동 산정 → 이 값 무시) */
    private BigDecimal vacReqUseDay;

    /* 부여 사유 - 사원이 기안 시 입력 */
    private String vacReqReason;

    /* 임신 주수 - MISCARRIAGE(유산·사산휴가) 시 필수. 주수 → 일수 자동 산정 근거 */
    /*   ≤11주: 5일 / 12~15: 10일 / 16~21: 30일 / 22~27: 60일 / ≥28: 90일 */
    private Integer pregnancyWeeks;

    /* 최종 결재자 ID (참고용, 알림 필요 시 확장) */
    private Long finalApproverEmpId;
}
