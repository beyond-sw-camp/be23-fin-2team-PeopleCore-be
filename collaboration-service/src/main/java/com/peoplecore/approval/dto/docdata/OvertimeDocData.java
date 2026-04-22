package com.peoplecore.approval.dto.docdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/* 초과근로신청서 docData 파싱 대상 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OvertimeDocData {
    /* 초과근로 대상 일자 */
    private LocalDateTime otDate;
    /* 계획 시작 시각 */
    private LocalDateTime otPlanStart;
    /* 계획 종료 시각 */
    private LocalDateTime otPlanEnd;
    /* 신청 사유 */
    private String otReason;
}
