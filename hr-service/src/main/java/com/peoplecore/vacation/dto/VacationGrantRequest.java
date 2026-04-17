package com.peoplecore.vacation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/* 관리자 휴가 부여 요청 - 다수 사원 일괄 */
/* 화면: 유형 선택 + 사원 다중 선택 + 일수 + (선택) 만료일 + 사유 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationGrantRequest {

    /* 부여할 휴가 유형 ID */
    private Long typeId;

    /* 부여 대상 사원 ID 목록 */
    private List<Long> empIds;

    /* 부여 일수 (양수) */
    private BigDecimal days;

    /* 회기 연도. null 이면 today.getYear() */
    private Integer year;

    /* 만료일. null 이면 무기한 (특수휴가 등). 관리자가 지정 */
    private LocalDate expiresAt;

    /* 부여 사유 - 감사 로그 (VacationLedger.reason) */
    private String reason;
}