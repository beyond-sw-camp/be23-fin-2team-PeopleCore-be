package com.peoplecore.attendance.dto;

import com.peoplecore.attendance.entity.WorkGroup;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/* 본인 근무그룹 응답 DTO - 휴가 사용 신청 모달의 시간 계산 + 근무요일 판정용 */
/* 반반차 계산 시 휴게시간 제외 (프론트가 breakStart/End 사이를 제외하고 4등분) */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MyWorkGroupResponseDto {

    /* 근무 그룹 ID */
    private Long workGroupId;

    /* 근무 그룹 명 - 화면 보조 표시용 */
    private String groupName;

    /* 출근 시각 - 프론트 vacReqOption 시각 계산 기준 */
    private LocalTime startTime;

    /* 퇴근 시각 */
    private LocalTime endTime;

    /* 휴게 시작 시각 - 반반차/반차 계산 시 제외 구간 */
    private LocalTime breakStart;

    /* 휴게 종료 시각 */
    private LocalTime breakEnd;

    /* 근무 요일 비트마스크 - 프론트 "선택 날짜가 근무일인지" 판정용 */
    /* 월=1(bit0), 화=2(bit1), 수=4, 목=8, 금=16, 토=32, 일=64(bit6) */
    /* 예: 31 = 월~금, 127 = 월~일 */
    private Integer workDayBitmask;

    public static MyWorkGroupResponseDto from(WorkGroup wg) {
        return MyWorkGroupResponseDto.builder()
                .workGroupId(wg.getWorkGroupId())
                .groupName(wg.getGroupName())
                .startTime(wg.getGroupStartTime())
                .endTime(wg.getGroupEndTime())
                .breakStart(wg.getGroupBreakStart())
                .breakEnd(wg.getGroupBreakEnd())
                .workDayBitmask(wg.getGroupWorkDay())
                .build();
    }
}
