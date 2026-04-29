package com.peoplecore.approval.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 결재 대기 건수 단건 응답 DTO
 * 헤더 배지/탭 카운터처럼 숫자 하나만 필요할 때 사용
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaitingCountResponse {
    /** 내가 지금 결재해야 할 문서 개수 */
    private long waiting;
}
