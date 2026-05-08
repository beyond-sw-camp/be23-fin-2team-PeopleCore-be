package com.peoplecore.client.dto;

import com.peoplecore.event.VacationSlotItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/* hr-service /internal/vacation/validate-request 호출용 Request Body */
/* 휴가 신청 사전 검증 - 결재 문서 생성 전 collab 가 동기로 호출 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationValidateRequest {

    /* 신청자 사원 ID */
    private Long empId;

    /* 휴가 유형 ID (VacationType.typeId) */
    private Long infoId;

    /* 휴가 슬롯 - N ≥ 1 */
    private List<VacationSlotItem> items;
}
