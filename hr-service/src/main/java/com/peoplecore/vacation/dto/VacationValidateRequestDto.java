package com.peoplecore.vacation.dto;

import com.peoplecore.event.VacationSlotItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/* 휴가 신청 사전 검증 요청 DTO - 내부 서비스 간 호출용 (collab → hr) */
/* companyId 는 X-User-Company 헤더로 받음 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationValidateRequestDto {

    /* 신청자 사원 ID */
    private Long empId;

    /* 휴가 유형 ID (VacationType.typeId) */
    private Long infoId;

    /* 휴가 슬롯 - 진실의 원천. N ≥ 1, 비어있으면 검증에서 VACATION_REQ_ITEMS_EMPTY */
    private List<VacationSlotItem> items;
}
