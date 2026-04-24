package com.peoplecore.vacation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/* 전사 휴가 관리 기간 조회 결과 - 사원 1명 = 1 entry (중복 제거) */
/* "이 기간 몇 명이 휴가인가" UX - 사원별 요약 (기간 내 총 사용일수 / 최초~최종 시점 / 신청 건수) */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationAdminPeriodResponseDto {

    /* 사원 ID */
    private Long empId;

    /* 사원명 (스냅샷 중 하나) */
    private String empName;

    /* 부서명 (스냅샷 중 하나) */
    private String deptName;

    /* 기간 내 휴가 합계 일수 - statuses 필터에 따름 */
    private BigDecimal totalDays;

    /* 기간 내 최초 시작 시점 */
    private LocalDateTime firstStartAt;

    /* 기간 내 최종 종료 시점 */
    private LocalDateTime lastEndAt;

    /* 기간 내 신청(슬롯) 건수 - 참고용 */
    private Long requestCount;
}
