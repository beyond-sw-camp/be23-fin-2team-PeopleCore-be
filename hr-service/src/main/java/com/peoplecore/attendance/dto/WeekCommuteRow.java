package com.peoplecore.attendance.dto;

/* 주 범위 CommuteRecord 행 — AttendanceAggregateQueryRepository 조회 결과 */

import com.peoplecore.attendance.entity.WorkStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeekCommuteRow {

    /* 사원 PK */
    private Long empId;

    /* 근무일 */
    private LocalDate workDate;

    /* 하루 최종 근태 상태 (ABSENT/NORMAL/LATE 등). 체크인 없으면 null */
    private WorkStatus workStatus;

    /* 체크인~체크아웃 분. 체크아웃 전이면 null */
    private Long minutes;
}
