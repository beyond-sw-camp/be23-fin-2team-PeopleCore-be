package com.peoplecore.attendance.dto;

import com.peoplecore.attendance.entity.OtExceedAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 모달 진입 시 잔여 초과근로시간 응답 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OvertimeRemainingResDto {

    /** 정책 주간 최대 근무 분 (시간 × 60) */
    private Integer weeklyMaxMinutes;

    /** 주간 기본 근로 분 = (실근무/일 × 근무요일 수). 실근무 = 종업-시업-휴게 */
    private Integer baseWorkMinutes;

    /** 주간 OT 최대 버퍼 = weeklyMax - baseWork (정책상 쓸 수 있는 OT 상한) */
    private Integer maxOvertimeBufferMinutes;

    /** 이번주 PENDING+APPROVED 신청 누적 분 */
    private Long weekUsedMinutes;

    /** 잔여 OT 분 = max(0, maxBuffer - weekUsed) */
    private Integer remainingMinutes;

    /** NOTIFY / BLOCK — 프론트 버튼 비활성화 판단용 */
    private OtExceedAction exceedAction;
}
