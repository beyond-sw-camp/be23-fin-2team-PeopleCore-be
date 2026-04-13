package com.peoplecore.attendance.dto;

import com.peoplecore.attendance.entity.AttendanceCardType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Map;

/*
 * 근태 현황 "일자별" 탭 상단 10개 카드 카운트 응답.
 * 필드:
 *  - date   : 조회 기준일
 *  - counts : 카드 타입별 사원 수 (AttendanceCardType 10개 전부 포함, 없으면 0)
 * 판정 규칙상 한 사원이 여러 카드에 동시에 잡힐 수 있으므로 counts 총합 ≠ 사원 수.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceDailySummaryResDto {

    /** 조회 기준일 */
    private LocalDate date;

    /** 카드 타입별 카운트 */
    private Map<AttendanceCardType, Integer> counts;
}