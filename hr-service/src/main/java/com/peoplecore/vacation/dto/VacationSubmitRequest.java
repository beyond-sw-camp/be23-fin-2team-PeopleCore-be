package com.peoplecore.vacation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** "확인" 클릭 시 사원 입력값. VacationReq insert 후 결재문서 작성 페이지 prefill */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationSubmitRequest {

    /** 휴가 유형 ID (VacationInfo) */
    private Long infoId;

    /** 휴가 시작 일시 */
    private LocalDateTime vacReqStartat;

    /** 휴가 종료 일시 */
    private LocalDateTime vacReqEndat;

    /** 사용일수 (종일 1.0 / 반차 0.5 / 반반차 0.25) */
    private BigDecimal vacReqUseDay;

    /** 휴가 사유 */
    private String vacReqReason;
}
