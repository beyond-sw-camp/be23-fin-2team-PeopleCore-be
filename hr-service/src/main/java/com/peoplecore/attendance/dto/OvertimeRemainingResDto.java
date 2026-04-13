package com.peoplecore.attendance.dto;

import com.peoplecore.attendance.entity.OtExceedAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 모달 진입 시 잔여 초과근로시간 응답. weekUsed = 이번주 PENDING+APPROVED 합계 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OvertimeRemainingResDto {

    /** 정책 주간 최대 근무 분 (시간 × 60) */
    private Integer weeklyMaxMinutes;

    /** 이번주 PENDING+APPROVED 신청의 (planEnd-planStart) 합계 분 */
    private Long weekUsedMinutes;

    /** 잔여 분 (max - used, 음수 시 0) */
    private Integer remainingMinutes;

    /** NOTIFY / BLOCK — 프론트 버튼 비활성화 판단용 */
    private OtExceedAction exceedAction;
}
