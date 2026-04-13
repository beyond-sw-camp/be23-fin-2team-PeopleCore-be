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

    private Long otPolicyId;

    /* 초과 근무 신청 최소 단위*/
    private OtMinUnit otMinUnit;

    /*사전 결재 필요 여부 */
    private Boolean otPolicyBefore;

    /*사후 결재 필요 여부 */
    private Boolean otPolicyAfter;

    /* 주간 최대 근무 시간*/
    private Integer otPolicyWeeklyMaxHour;

    /* 주간 근무 경고 시간*/
    private Integer otPolicyWarningHour;

    /* 주간 근로 시간 초과시 처리 방법 ( 알림, 초과근무 신청 자동 차단 */
    private OtExceedAction otExceedAction;

    public static OverTimePolicyResDto from(OvertimePolicy entity) {
        return OverTimePolicyResDto.builder()
                .otPolicyId(entity.getOtPolicyId())
                .otMinUnit(entity.getOtMinUnit())
                .otPolicyBefore(entity.getOtPolicyBefore())
                .otPolicyAfter(entity.getOtPolicyAfter())
                .otPolicyWeeklyMaxHour(entity.getOtPolicyWeeklyMaxHour())
                .otPolicyWarningHour(entity.getOtPolicyWarningHour())
                .otExceedAction(entity.getOtExceedAction())
                .build();
    }

    public static OverTimePolicyResDto defaultPolicy() {
        return OverTimePolicyResDto.builder()
                .otMinUnit(OtMinUnit.FIFTEEN)
                .otPolicyBefore(true)
                .otPolicyAfter(false)
                .otPolicyWeeklyMaxHour(52)
                .otPolicyWarningHour(45)
                .otExceedAction(OtExceedAction.NOTIFY)
                .build();
    }
}
