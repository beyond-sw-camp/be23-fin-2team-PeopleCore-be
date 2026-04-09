package com.peoplecore.approval.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentCountResponse {
    /* 결재하기 */
    private long waiting;
    private long ccView;
    private long upcoming;

    /* 개인 문서함 */
    private long draft;
    private long temp;
    private long approved;
    private long ccViewBox;
    private long inbox;
}
