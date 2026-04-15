package com.peoplecore.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuggestItem {

    private String type;
    private String sourceId;
    private String title;
    /** 사원의 부서명 / 결재의 기안자명 등 타입별 보조 라벨. 없을 수 있음. */
    private String subLabel;
    private String link;
}
