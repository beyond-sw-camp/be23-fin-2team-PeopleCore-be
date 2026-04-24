package com.peoplecore.vacation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/* 전사 휴가 관리 기간 조회 - 건별 row (같은 사원이 여러 건 신청 시 각각 반환) */
/* 요약 집계는 VacationAdminPeriodPageResponse 의 메타 필드(uniqueEmployeeCount/totalUseDays) 참조 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationAdminPeriodResponseDto {

    /* 신청 ID (PK) - 상세/취소 API 연동용 */
    private Long requestId;

    /* 사원 ID - 같은 값이 여러 row 에 반복될 수 있음 (사원 중복 허용) */
    private Long empId;

    /* 사원명 (신청 당시 스냅샷) */
    private String empName;

    /* 부서명 (신청 당시 스냅샷) */
    private String deptName;

    /* 휴가 유형명 (연차/월차/반차 등 표시명) */
    private String vacationTypeName;

    /* 이 건의 시작 일시 - 각 신청의 실제 기간이라 요약 오해 없음 */
    private LocalDateTime requestStartAt;

    /* 이 건의 종료 일시 */
    private LocalDateTime requestEndAt;

    /* 이 건의 사용 일수 (1.0=종일, 0.5=반차, 0.125=1시간 등) */
    private BigDecimal useDays;
}
