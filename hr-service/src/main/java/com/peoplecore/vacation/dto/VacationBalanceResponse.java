package com.peoplecore.vacation.dto;

import com.peoplecore.vacation.entity.VacationBalance;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/* 잔여 응답 DTO - 화면 "내 잔여" / 관리자 조회 */
/* available = total - used - pending - expired (프론트 계산 이중 방지) */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationBalanceResponse {

    /* 잔여 ID (PK) */
    private Long balanceId;

    /* 유형 ID */
    private Long typeId;

    /* 유형 코드 (MONTHLY/ANNUAL 또는 회사 정의) */
    private String typeCode;

    /* 유형 표시명 */
    private String typeName;

    /* 회기 연도 */
    private Integer balanceYear;

    /* 총 적립 (누적, 감소하지 않음) */
    private BigDecimal totalDays;

    /* 승인 사용 누적 */
    private BigDecimal usedDays;

    /* 결재 대기 예약 */
    private BigDecimal pendingDays;

    /* 만료 소멸 누적 */
    private BigDecimal expiredDays;

    /* 사용 가능 = total - used - pending - expired */
    private BigDecimal availableDays;

    /* 최초 적립일 */
    private LocalDate grantedAt;

    /* 만료일 (null=무기한) */
    private LocalDate expiresAt;

    public static VacationBalanceResponse from(VacationBalance b) {
        return VacationBalanceResponse.builder()
                .balanceId(b.getBalanceId())
                .typeId(b.getVacationType().getTypeId())
                .typeCode(b.getVacationType().getTypeCode())
                .typeName(b.getVacationType().getTypeName())
                .balanceYear(b.getBalanceYear())
                .totalDays(b.getTotalDays())
                .usedDays(b.getUsedDays())
                .pendingDays(b.getPendingDays())
                .expiredDays(b.getExpiredDays())
                .availableDays(b.getAvailableDays())
                .grantedAt(b.getGrantedAt())
                .expiresAt(b.getExpiresAt())
                .build();
    }
}