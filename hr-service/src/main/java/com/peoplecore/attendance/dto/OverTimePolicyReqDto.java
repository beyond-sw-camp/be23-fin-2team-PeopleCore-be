package com.peoplecore.attendance.dto;


import com.peoplecore.attendance.entity.OtExceedAction;
import com.peoplecore.attendance.entity.OtMinUnit;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class OverTimePolicyReqDto {
    /*초과 근무 신청 최소 단위*/
    private OtMinUnit otMinUnit;
    /* 주간 최대 근무 시간 */
    private Integer otPolicyWeeklyMaxHour;
    /* 주간 근무 경고 시간*/
    private Integer otPolicyWarningHour;
    /* 주간 초과 시 처리 방식 */
    private OtExceedAction otExceedAction;
}
