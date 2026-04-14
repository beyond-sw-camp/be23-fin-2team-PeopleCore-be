package com.peoplecore.attendance.dto;

import com.peoplecore.attendance.entity.OtStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 주간 초과근무 신청 이력 (모달 하단 신청이력 테이블용). DRAFT 제외 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OvertimeWeekHistoryResDto {

    /** 조회 주 시작일 (월요일로 정규화됨) */
    private LocalDate weekStart;

    /** 조회 주 종료일 (weekStart + 6) */
    private LocalDate weekEnd;

    /** 이력 목록 — otDate ASC, otPlanStart ASC */
    private List<Item> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Item {

        /** OvertimeRequest PK */
        private Long otId;

        /** 결재 상태 (PENDING/APPROVED/REJECTED/CANCELED) */
        private OtStatus otStatus;

        /** 신청 기준 날짜 (LocalDateTime 에서 date 부분만) */
        private LocalDate otDate;

        /** 계획 시작 시각 */
        private LocalDateTime otPlanStart;

        /** 계획 종료 시각 */
        private LocalDateTime otPlanEnd;

        /** otPlanEnd - otPlanStart 분 */
        private Long otPlanMinutes;

        /** 신청 사유 */
        private String otReason;
    }
}
