package com.peoplecore.attendance.dto;


/* 주 범위 CoummuteRecord 행 */

import com.peoplecore.attendance.entity.CheckInStatus;
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

    /*사원 PK */
    private Long empId;

    /*근무일 */
    private LocalDate workDate;

    /*체크인 상태 */
    private CheckInStatus checkInStatus;

    /*체크인 체크아웃 분 체크 아웃전이면 null*/
    private Long minutes;
}
