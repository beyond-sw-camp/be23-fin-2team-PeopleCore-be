package com.peoplecore.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 사원 일별 근무 현황 API 응답 루트.
 * - header : 상단 4카드 영역 데이터
 * - history: 일별 근무 현황 페이지 (workDate DESC 정렬)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceEmployeeHistoryResDto {

    /** 상단 요약 카드 영역 */
    private AttendanceEmployeeHistoryHeaderDto header;

    /** 일별 근무 현황 (페이지네이션) */
    private PagedResDto<AttendanceEmployeeHistoryRowResDto> history;
}
