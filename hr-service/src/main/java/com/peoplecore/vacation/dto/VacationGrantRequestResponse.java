package com.peoplecore.vacation.dto;

import com.peoplecore.vacation.entity.VacationGrantRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/* 휴가 부여 신청 응답 DTO - 사원 이력 / 관리자 조회 공통 */
/* 스냅샷 필드 사용 (조직개편 후에도 신청 당시 정보 보존) */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationGrantRequestResponse {

    private Long requestId;

    /* 휴가 유형 */
    private Long typeId;
    private String typeCode;
    private String typeName;

    /* 신청 사원 (스냅샷) */
    private Long empId;
    private String empName;
    private String empDeptName;
    private String empGrade;
    private String empTitle;

    /* 부여 요청 일수 / 사유 */
    private BigDecimal useDays;
    private String reason;

    /* 상태 */
    private String status;

    /* 처리 정보 */
    private Long managerId;
    private LocalDateTime processedAt;
    private String rejectReason;

    /* 결재 문서 참조 */
    private Long approvalDocId;

    /* MISCARRIAGE 전용 메타 - 그 외 유형은 null */
    private Integer pregnancyWeeks;

    private LocalDateTime createdAt;

    public static VacationGrantRequestResponse from(VacationGrantRequest r) {
        return VacationGrantRequestResponse.builder()
                .requestId(r.getRequestId())
                .typeId(r.getVacationType().getTypeId())
                .typeCode(r.getVacationType().getTypeCode())
                .typeName(r.getVacationType().getTypeName())
                .empId(r.getEmployee().getEmpId())
                .empName(r.getRequestEmpName())
                .empDeptName(r.getRequestEmpDeptName())
                .empGrade(r.getRequestEmpGrade())
                .empTitle(r.getRequestEmpTitle())
                .useDays(r.getRequestUseDays())
                .reason(r.getRequestReason())
                .status(r.getRequestStatus().name())
                .managerId(r.getManager() != null ? r.getManager().getEmpId() : null)
                .processedAt(r.getRequestProcessedAt())
                .rejectReason(r.getRequestRejectReason())
                .approvalDocId(r.getApprovalDocId())
                .pregnancyWeeks(r.getPregnancyWeeks())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
