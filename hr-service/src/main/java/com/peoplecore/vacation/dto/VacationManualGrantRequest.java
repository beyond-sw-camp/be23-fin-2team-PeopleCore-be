package com.peoplecore.vacation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/* 관리자 휴가 수동 부여/차감 Request - 다수 사원 일괄 처리 */
/* 사원의 법정휴가 부여 신청 엔티티(VacationGrantRequest) 와 구분하기 위한 네이밍 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationManualGrantRequest {

    /* 부여할 휴가 유형 ID */
    private Long typeId;

    /* 부여 대상 사원 ID 목록 */
    private List<Long> empIds;

    /* 조정 일수 - 양수=부여, 음수=차감. 0/null 은 BAD_REQUEST. 서비스에서 부호 분기 */
    private BigDecimal days;

    /* 회기 연도. null 이면 today.getYear() */
    private Integer year;

    /* 만료일. null 이면 무기한 (특수휴가 등). 관리자가 지정 */
    private LocalDate expiresAt;

    /* 부여 사유 - 감사 로그 (VacationLedger.reason) */
    private String reason;
}
