package com.peoplecore.vacation.dto;

import com.peoplecore.vacation.entity.UseOption;
import com.peoplecore.vacation.entity.VacationRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/* 전사 휴가 관리 페이지 - 기간 조회 결과 DTO */
/* 사원명/부서는 VacationRequest 스냅샷 필드(request_emp_name/request_emp_dept_name) 활용 → Employee 조인 불필요 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationAdminPeriodResponse {

    /* 신청 ID (PK) - 상세 조회 연결용 */
    private Long requestId;

    /* 사원명 (신청 당시 스냅샷) */
    private String empName;

    /* 부서명 (신청 당시 스냅샷) */
    private String deptName;

    /* 휴가 유형명 - VacationType 조인 */
    private String typeName;

    /* 사용 옵션 - FULL_DAY(종일) / HALF_DAY(반차) / QUARTER_DAY(반반차) */
    private UseOption useOption;

    /* 휴가 시작 일시 */
    private LocalDateTime startAt;

    /* 휴가 종료 일시 */
    private LocalDateTime endAt;

    /* 사용 일수 - 1.0 / 0.5 / 0.25 / N.0 */
    private BigDecimal useDays;

    /* 상태 - PENDING/APPROVED/REJECTED/CANCELED */
    private String status;

    /* VacationRequest → DTO 변환 - VacationType fetch join 된 엔티티 기대 */
    public static VacationAdminPeriodResponse from(VacationRequest r) {
        return VacationAdminPeriodResponse.builder()
                .requestId(r.getRequestId())
                .empName(r.getRequestEmpName())
                .deptName(r.getRequestEmpDeptName())
                .typeName(r.getVacationType().getTypeName())
                .useOption(UseOption.fromDays(r.getRequestUseDays()))
                .startAt(r.getRequestStartAt())
                .endAt(r.getRequestEndAt())
                .useDays(r.getRequestUseDays())
                .status(r.getRequestStatus().name())
                .build();
    }
}
