package com.peoplecore.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** "확인" 클릭 시 사원 입력값. 이 값으로 OvertimeRequest insert 후 결재문서 작성 페이지 prefill */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OvertimeSubmitRequest {

    /** 신청 기준 날짜 (사전 신청 시 미래 가능) */
    private LocalDateTime otDate;

    /** 계획 시작 시각 */
    private LocalDateTime otPlanStart;

    /** 계획 종료 시각 */
    private LocalDateTime otPlanEnd;

    /** 신청 사유 */
    private String otReason;
}
