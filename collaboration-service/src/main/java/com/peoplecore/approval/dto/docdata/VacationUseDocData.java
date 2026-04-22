package com.peoplecore.approval.dto.docdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/* 휴가 사용 신청서(VACATION_REQUEST) docData 파싱 대상 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VacationUseDocData {
    /* 휴가 유형 PK (VacationType) */
    private Long infoId;
    /* 휴가 시작 */
    private LocalDateTime vacReqStartat;
    /* 휴가 종료 */
    private LocalDateTime vacReqEndat;
    /* 사용 일수 */
    private BigDecimal vacReqUseDay;
    /* 사유 */
    private String vacReqReason;
}
