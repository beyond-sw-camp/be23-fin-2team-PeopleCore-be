package com.peoplecore.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/*주 범위와 겹치는 휴가자 승인 반환 dto
 * 한 사원이 한 주에 연속으로 휴가를 쓸 수 있어 List로 반환
 * */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeekVacationRow {

    /*사원 PK */
    private Long empId;

    /*휴가 시작 일시 */
    private LocalDateTime startAt;

    /*휴가 종료 일시 */
    private LocalDateTime endAt;

    /*사용 일 수 -> 반차, 종일, 반반차 골려 */
    private BigDecimal vacReqUseDay;
}
