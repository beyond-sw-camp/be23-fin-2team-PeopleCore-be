package com.peoplecore.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/* 휴가 슬롯 1건 - 한 날짜 내 시작/종료/사용일수. 비연속/부분 휴가 표현 단위 */
/* 결재문서 1건이 여러 슬롯(N ≥ 1)을 가질 수 있음. collab(DocData) → hr(Event/Service) 공용 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class VacationSlotItem {

    /* 슬롯 시작 일시 (endAt 과 같은 날이어야 함) */
    private LocalDateTime startAt;

    /* 슬롯 종료 일시 (startAt 과 같은 날이어야 함) */
    private LocalDateTime endAt;

    /* 슬롯 사용 일수 - 1.0=종일 / 0.5=반차 / 0.25=반반차 */
    private BigDecimal useDay;
}
