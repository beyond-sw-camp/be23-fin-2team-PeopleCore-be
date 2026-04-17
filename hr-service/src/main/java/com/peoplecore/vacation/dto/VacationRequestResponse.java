package com.peoplecore.vacation.dto;

import com.peoplecore.vacation.entity.VacationRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/* 휴가 신청 응답 DTO - 사원 이력 / 관리자 조회 공통 */
/* 스냅샷 필드 사용 (조직개편 후에도 신청 당시 정보 보존) */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationRequestResponse {

    private Long requestId;

    /* 휴가 유형 */
    private Long typeId;
    private String typeCode;
    private String typeName;

    /* 신청 사원 */
    private Long empId;
    private String empName;
    private String empDeptName;
    private String empGrade;
    private String empTitle;

    /* 휴가 기간 */
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private BigDecimal useDays;

    /* 사유 / 상태 */
    private String reason;
    private String status;

    /* 처리 정보 (승인/반려/취소) */
    private Long managerId;
    private LocalDateTime processedAt;
    private String rejectReason;

    /* 결재 문서 참조 */
    private Long approvalDocId;

    private LocalDateTime createdAt;

    public static VacationRequestResponse from(VacationRequest r) {
        return VacationRequestResponse.builder()
                .requestId(r.getRequestId())
                .typeId(r.getVacationType().getTypeId())
                .typeCode(r.getVacationType().getTypeCode())
                .typeName(r.getVacationType().getTypeName())
                .empId(r.getEmployee().getEmpId())
                .empName(r.getRequestEmpName())
                .empDeptName(r.getRequestEmpDeptName())
                .empGrade(r.getRequestEmpGrade())
                .empTitle(r.getRequestEmpTitle())
                .startAt(r.getRequestStartAt())
                .endAt(r.getRequestEndAt())
                .useDays(r.getRequestUseDays())
                .reason(r.getRequestReason())
                .status(r.getRequestStatus().name())
                .managerId(r.getManager() != null ? r.getManager().getEmpId() : null)
                .processedAt(r.getRequestProcessedAt())
                .rejectReason(r.getRequestRejectReason())
                .approvalDocId(r.getApprovalDocId())
                .createdAt(r.getCreatedAt())
                .build();
    }
}