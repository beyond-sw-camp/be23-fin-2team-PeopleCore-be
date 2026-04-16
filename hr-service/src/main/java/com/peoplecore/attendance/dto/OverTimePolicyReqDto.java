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
/* 전부 분으로 통일 */
public class OverTimePolicyReqDto {
    /*초과 근무 신청 최소 단위*/
    private OtMinUnit otMinUnit;
    /* 주간 최대 근무 시간 */
    private Integer otPolicyWeeklyMaxMinutes;
    /* 주간 근무 경고 시간*/
    private Integer otPolicyWarningMinutes;
    /* 주간 초과 시 처리 방식 */
    private OtExceedAction otExceedAction;
}
