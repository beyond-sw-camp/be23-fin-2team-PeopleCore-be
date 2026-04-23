package com.peoplecore.approval.dto.docdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.peoplecore.event.VacationSlotItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/* 휴가 사용 신청서(VACATION_REQUEST) docData 파싱 대상 */
/* 프론트는 vacReqItems 배열로 날짜별 슬롯 전달. 비연속/부분 휴가 표현 단위 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VacationUseDocData {

    /* 휴가 유형 PK (VacationType) */
    private Long infoId;

    /* 휴가 사유 (그룹 공통) */
    private String vacReqReason;

    /* 휴가 슬롯 배열 - N ≥ 1. 각 슬롯은 같은 날 내 startAt/endAt/useDay */
    private List<VacationSlotItem> vacReqItems;
}
