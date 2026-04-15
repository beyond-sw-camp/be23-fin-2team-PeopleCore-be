package com.peoplecore.attendance.dto;

import com.peoplecore.attendance.entity.OtExceedAction;
import com.peoplecore.attendance.entity.OtMinUnit;
import com.peoplecore.attendance.entity.OvertimePolicy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class OverTimePolicyResDto {

    /*정책 pk */
    private Long otPolicyId;

    /* 초과 근무 신청 최소 단위*/
    private OtMinUnit otMinUnit;

    /* 주간 최대 근무 시간*/
    private Integer otPolicyWeeklyMaxMinutes;

    /* 주간 근무 경고 시간*/
    private Integer otPolicyWarningMinutes;

    /* 주간 근로 시간 초과시 처리 방법 ( 알림, 초과근무 신청 자동 차단 */
    private OtExceedAction otExceedAction;

    public static OverTimePolicyResDto from(OvertimePolicy entity) {
        return OverTimePolicyResDto.builder()
                .otPolicyId(entity.getOtPolicyId())
                .otMinUnit(entity.getOtMinUnit())
                .otPolicyWeeklyMaxMinutes(entity.getOtPolicyWeeklyMaxMinutes())
                .otPolicyWarningMinutes(entity.getOtPolicyWarningMinutes())
                .otExceedAction(entity.getOtExceedAction())
                .build();
    }

    public static OverTimePolicyResDto defaultPolicy() {
        return OverTimePolicyResDto.builder()
                .otMinUnit(OtMinUnit.FIFTEEN)
                .otPolicyWeeklyMaxMinutes(3120)
                .otPolicyWarningMinutes(2700)
                .otExceedAction(OtExceedAction.NOTIFY)
                .build();
    }
}
