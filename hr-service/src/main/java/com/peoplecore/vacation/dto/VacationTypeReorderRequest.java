package com.peoplecore.vacation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/* 휴가 유형 일괄 재정렬 요청 DTO - 관리자 드래그 앤 드롭 후 전체 순서 전송 */
/* 요청 배열의 모든 item 은 동일 회사 소속이어야 함 (서비스에서 검증) */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationTypeReorderRequest {

    /* 재정렬 대상 목록 - 각 item 은 typeId + 새 sortOrder */
    private List<Item> items;

    /* 재정렬 단위 - 유형 ID 와 새 정렬 순서 쌍 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Item {

        /* 대상 휴가 유형 ID (PK) */
        private Long typeId;

        /* 새 정렬 순서 - 1 기반 권장. 음수·0 도 허용(특수 정렬 필요 시) */
        private Integer sortOrder;
    }
}
