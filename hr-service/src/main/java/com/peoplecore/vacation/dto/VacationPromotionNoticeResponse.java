package com.peoplecore.vacation.dto;

import com.peoplecore.vacation.entity.VacationPromotionNotice;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/* 연차 촉진 통지 이력 응답 DTO */
/* 화면: 관리자 "촉진 이력" 탭 + 사원 "내 촉진 이력" */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationPromotionNoticeResponse {

    /* 통지 ID */
    private Long noticeId;

    /* 사원 ID - 관리자 화면 표시용 (사원 본인 조회 시엔 본인 ID) */
    private Long empId;

    /* 대상 회기 연도 */
    private Integer noticeYear;

    /* 통지 시점 잔여 일수 (사용 권고받은 일수) */
    private BigDecimal targetRemainingDays;

    /* 통지 단계 - "FIRST" / "SECOND" */
    private String noticeStage;

    /* 통지 발송 시각 */
    private LocalDateTime noticeSentAt;

    /* 사원 응답 (사용 계획 또는 회사 지정) - nullable */
    private String employeeResponse;

    /* 응답 시점 사용 예정/실제 일수 - nullable */
    private BigDecimal responseUsedDays;

    /* 응답 기록 시각 - nullable */
    private LocalDateTime responseRecordedAt;

    public static VacationPromotionNoticeResponse from(VacationPromotionNotice n) {
        return VacationPromotionNoticeResponse.builder()
                .noticeId(n.getNoticeId())
                .empId(n.getEmpId())
                .noticeYear(n.getNoticeYear())
                .targetRemainingDays(n.getTargetRemainingDays())
                .noticeStage(n.getNoticeStage())
                .noticeSentAt(n.getNoticeSentAt())
                .employeeResponse(n.getEmployeeResponse())
                .responseUsedDays(n.getResponseUsedDays())
                .responseRecordedAt(n.getResponseRecordedAt())
                .build();
    }
}