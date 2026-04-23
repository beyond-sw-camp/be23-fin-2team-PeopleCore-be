package com.peoplecore.vacation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/* 전사 휴가 관리 - 부서 선택 후 사원별 상세 row DTO */
/* 화면 테이블 1줄 = 사원 1명 + 선택 법정 유형(기본 ANNUAL) 기준 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepartmentMemberVacationResponseDto {

    /* 사원 ID - 행 클릭 시 상세 화면 진입 */
    private Long empId;

    /* 사원명 (예: "한소희") */
    private String empName;

    /* 직급명 (예: "대리") - 프론트에서 "한소희 대리" 로 결합 */
    private String empGrade;

    /* 소속 부서명 */
    private String deptName;

    /* 입사일 */
    private LocalDate empHireDate;

    /* 근속 연수 - 입사일 ~ 오늘 기준 만년. 1년 미만은 0 */
    private Integer serviceYears;

    /* 구분 유형 코드 (기본 ANNUAL) - 프론트 필터 파라미터로 전달된 값 그대로 */
    private String statutoryTypeCode;

    /* 구분 유형명 (예: "연차") */
    private String statutoryTypeName;

    /* 선택 법정 유형 사용 기간 시작 - balance.grantedAt 또는 회기 시작일 */
    private LocalDate periodStart;

    /* 선택 법정 유형 사용 기간 종료 - balance.expiresAt 또는 회기 종료일 */
    private LocalDate periodEnd;

    /* 잔여 법정 연차 - 법정 유형 available 합 (사원 단위 집계) */
    private BigDecimal statutoryAvailable;

    /* 잔여 특별 휴가 - 특별(회사 커스텀) 유형 available 합 */
    private BigDecimal specialAvailable;

    /* 사용 일수 - 전체 유형 used 합 */
    private BigDecimal usedDays;

    /* 총연차 - 법정+특별 total 합 */
    private BigDecimal totalDays;

    /* 발생 - 스케줄러 적립(ACCRUAL/INITIAL_GRANT) 합. 총연차 - 조정 으로 파생 */
    private BigDecimal accruedDays;

    /* 조정 - 관리자 수동 부여/차감 (ledger MANUAL_GRANT 합). 부호 포함(+/-) */
    private BigDecimal adjustedDays;

    /* 소진율(%) - 사용 / 총연차 × 100. total 0 이면 0 */
    private Integer usageRate;
}
