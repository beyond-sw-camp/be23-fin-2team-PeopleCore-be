package com.peoplecore.approval.dto.docdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/* 휴가 부여 신청서(VACATION_GRANT_REQUEST) docData 파싱 대상 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VacationGrantDocData {
    /* 휴가 유형 PK (VacationType) */
    private Long infoId;
    /* 부여 요청 일수 (MISCARRIAGE 는 서버에서 주수 기반 자동 산정) */
    private BigDecimal vacReqUseDay;
    /* 부여 사유 */
    private String vacReqReason;
    /* 임신 주수 - MISCARRIAGE 시 필수 */
    private Integer pregnancyWeeks;
}
