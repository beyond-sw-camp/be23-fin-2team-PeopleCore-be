package com.peoplecore.vacation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;

/* 전사 휴가 관리 기간 조회 응답 래퍼 - 건별 페이지 + 기간 메타 (휴가자 수/총 일수) */
/* page.content = 건별 목록 (사원 중복 허용), 메타 2개 = 중복 제거된 집계 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationAdminPeriodPageResponse {

    /* 건별 페이지 - totalElements 는 조건에 맞는 총 건수(row 수) */
    private Page<VacationAdminPeriodResponseDto> page;

    /* 해당 기간 휴가자 수 - distinct empId count (사원 중복 제거) */
    private Long uniqueEmployeeCount;

    /* 해당 기간 총 휴가 일수 - sum(useDays). 전사 휴가 소진 총량 */
    private BigDecimal totalUseDays;
}
