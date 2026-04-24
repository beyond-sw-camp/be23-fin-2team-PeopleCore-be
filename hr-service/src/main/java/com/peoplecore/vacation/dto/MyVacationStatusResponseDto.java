package com.peoplecore.vacation.dto;

import com.peoplecore.vacation.entity.VacationBalance;
import com.peoplecore.vacation.entity.VacationRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/* 내 휴가 현황 응답 DTO - 휴가현황 페이지 단일 endpoint */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MyVacationStatusResponseDto {

    /* 조회 연도 */
    private Integer year;

    /* 연차 요약 - balance_year=year 의 ANNUAL row (UNIQUE 제약으로 0 또는 1건) */
    private AnnualSummary annual;

    /* 연차 외 보유 balance - 월차/리프레시/출산/공가 등 (balance_year=year) */
    private List<OtherBalance> others;

    /* 예정 휴가 - PENDING 전체 + (APPROVED AND endAt >= now) */
    private List<RequestItem> upcoming;

    /* 지난 휴가 - (APPROVED AND endAt < now) OR REJECTED OR CANCELED */
    private List<RequestItem> past;


    /* 연차 카드 - periodStart/periodEnd 는 balance 의 grantedAt/expiresAt 원본 */
    /* HIRE: 예) 2026-09-01 ~ 2027-08-31 / FISCAL: 예) 2026-01-01 ~ 2026-12-31 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AnnualSummary {
        private LocalDate periodStart;
        /* null=무기한 */
        private LocalDate periodEnd;
        private BigDecimal totalDays;
        private BigDecimal usedDays;
        private BigDecimal pendingDays;
        private BigDecimal expiredDays;
        /* 사용 가능 = total - used - pending - expired */
        private BigDecimal availableDays;
    }

    /* 기타 휴가 카드 - 연차 외 유형 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OtherBalance {
        private Long balanceId;
        private Long typeId;
        private String typeCode;
        private String typeName;
        private Integer balanceYear;
        private BigDecimal totalDays;
        private BigDecimal availableDays;
        private LocalDate grantedAt;
        /* null=무기한 */
        private LocalDate expiresAt;

        public static OtherBalance from(VacationBalance b) {
            return OtherBalance.builder()
                    .balanceId(b.getBalanceId())
                    .typeId(b.getVacationType().getTypeId())
                    .typeCode(b.getVacationType().getTypeCode())
                    .typeName(b.getVacationType().getTypeName())
                    .balanceYear(b.getBalanceYear())
                    .totalDays(b.getTotalDays())
                    .availableDays(b.getAvailableDays())
                    .grantedAt(b.getGrantedAt())
                    .expiresAt(b.getExpiresAt())
                    .build();
        }
    }

    /* 예정/지난 휴가 항목 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RequestItem {
        private Long requestId;
        /* PENDING/APPROVED/REJECTED/CANCELED */
        private String status;
        private Long typeId;
        private String typeCode;
        private String typeName;
        private BigDecimal useDays;
        private LocalDateTime startAt;
        private LocalDateTime endAt;
        private Long approvalDocId;

        public static RequestItem from(VacationRequest r) {
            return RequestItem.builder()
                    .requestId(r.getRequestId())
                    .status(r.getRequestStatus().name())
                    .typeId(r.getVacationType().getTypeId())
                    .typeCode(r.getVacationType().getTypeCode())
                    .typeName(r.getVacationType().getTypeName())
                    .useDays(r.getRequestUseDays())
                    .startAt(r.getRequestStartAt())
                    .endAt(r.getRequestEndAt())
                    .approvalDocId(r.getApprovalDocId())
                    .build();
        }
    }
}
