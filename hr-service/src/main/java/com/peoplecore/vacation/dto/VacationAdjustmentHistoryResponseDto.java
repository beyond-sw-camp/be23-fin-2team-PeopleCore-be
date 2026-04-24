package com.peoplecore.vacation.dto;

import com.peoplecore.vacation.entity.LedgerEventType;
import com.peoplecore.vacation.entity.VacationLedger;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/* 관리자 수동 조정 이력 응답 - MANUAL_GRANT / MANUAL_USED 만 */
/* 사용처: GET /vacation/balances/{empId}/adjustments */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationAdjustmentHistoryResponseDto {

    /* Ledger PK - 스크롤 페이지 내 고유 식별자 */
    private Long ledgerId;

    /* MANUAL_GRANT(부여) / MANUAL_USED(차감/사용 기록) */
    private LedgerEventType eventType;

    /* 변동 일수 - 양수=부여, 음수=차감/사용 (Ledger.changeDays 원본 부호 그대로) */
    private BigDecimal changeDays;

    /* 휴가 유형 ID */
    private Long typeId;

    /* 휴가 유형명 - 화면 표시용 (연차/월차/특별휴가 등) */
    private String typeName;

    /* 회기 연도 - 어느 해 balance 에 대한 조정이었는지 */
    private Integer balanceYear;

    /* 조정한 관리자 사원 ID */
    private Long managerId;

    /* 관리자 이름 - Employee 조회 결과. null 가능 (퇴사/삭제 등 예외) */
    private String managerName;

    /* 사유 - 관리자 입력 자유 텍스트. nullable */
    private String reason;

    /* 조정 시각 - BaseTimeEntity.createdAt */
    private LocalDateTime createdAt;

    /* 엔티티 + 관리자 이름 → DTO. managerName 은 서비스에서 bulk 조회 후 주입 */
    public static VacationAdjustmentHistoryResponseDto from(VacationLedger ledger, String managerName) {
        return VacationAdjustmentHistoryResponseDto.builder()
                .ledgerId(ledger.getLedgerId())
                .eventType(ledger.getEventType())
                .changeDays(ledger.getChangeDays())
                .typeId(ledger.getVacationBalance().getVacationType().getTypeId())
                .typeName(ledger.getVacationBalance().getVacationType().getTypeName())
                .balanceYear(ledger.getVacationBalance().getBalanceYear())
                .managerId(ledger.getManagerId())
                .managerName(managerName)
                .reason(ledger.getReason())
                .createdAt(ledger.getCreatedAt())
                .build();
    }
}
