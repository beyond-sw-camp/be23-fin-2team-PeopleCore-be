package com.peoplecore.attendence.dto;


import com.peoplecore.attendence.entity.OtExceedAction;
import com.peoplecore.attendence.entity.OtMinUnit;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class OverTimePolicyReqDto {
    private OtMinUnit otMinUnit;
    private Boolean otPolicyBefore;
    private Boolean otPolicyAfter;
    private Integer otPolicyWeeklyMaxHour;
    private Integer otPolicyWarningHour;
    private OtExceedAction otExceedAction;
}
