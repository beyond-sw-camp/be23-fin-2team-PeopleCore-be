package com.peoplecore.pay.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DB/DC 퇴직연금 적립금 조회 응답 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PensionInfoResDto {

    private String pensionType;          // DB / DC / severance
    private String pensionTypeLabel;     // 표시용 라벨
    private String pensionProvider;      // 퇴직연금 운용사
    private String pensionAccount;       // 퇴직연금 계좌
    private LocalDate depositStartDate;  // 적립 시작일 (입사일 기준)
    private LocalDateTime lastDepositDate;   // 최근 적립일
    private Long monthlyDeposit;         // 월 적립액 (DC형: 기준급여의 1/12)
    private Long totalDeposited;         // 누적 적립금액

    public static String toPensionLabel(String pensionType) {
        if (pensionType == null) return "퇴직금";
        return switch (pensionType) {
            case "DB" -> "DB형 (확정급여형)";
            case "DC" -> "DC형 (확정기여형)";
            case "DB_DC" -> "DB+DC형";
            default -> "퇴직금";
        };
    }
}
