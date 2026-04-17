package com.peoplecore.vacation.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/* 연차 사용 촉진 통지 이력 - 근로기준법 제61조 대응. 통지 발송 후 INSERT */
@Entity
@Table(
        name = "vacation_promotion_notice",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_vacation_notice_company_emp_year_stage",
                columnNames = {"company_id", "emp_id", "notice_year", "notice_stage"}
        ),
        indexes = {
                @Index(name = "idx_vacation_notice_company_sent",
                        columnList = "company_id, notice_sent_at")
        }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationPromotionNotice extends BaseTimeEntity {

    /* 통지 단계 String 상수 - enum 안 만듦 */
    public static final String STAGE_FIRST  = "FIRST";
    public static final String STAGE_SECOND = "SECOND";

    /* 통지 ID (PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notice_id")
    private Long noticeId;

    /* 회사 ID */
    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    /* 사원 ID */
    @Column(name = "emp_id", nullable = false)
    private Long empId;

    /* 대상 잔여 - LAZY */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "balance_id", nullable = false)
    private VacationBalance vacationBalance;

    /* 대상 회기 연도 - balance.balanceYear 와 동일 (조회 편의) */
    @Column(name = "notice_year", nullable = false)
    private Integer noticeYear;

    /* 통지 시점 잔여 일수 - 사원이 사용 권고받은 일수 */
    @Column(name = "target_remaining_days", nullable = false, precision = 5, scale = 2)
    private BigDecimal targetRemainingDays;

    /* 통지 단계 - "FIRST" / "SECOND". UNIQUE per (company,emp,year) */
    @Column(name = "notice_stage", nullable = false, length = 20)
    private String noticeStage;

    /* 통지 발송 시각 - 알림 시스템 발송 완료 시 기록 */
    @Column(name = "notice_sent_at", nullable = false)
    private LocalDateTime noticeSentAt;

    /* 사원 응답 - 1차: 사원 사용 계획 텍스트, 2차: 회사 지정 사용일자 */
    @Column(name = "employee_response", length = 500)
    private String employeeResponse;

    /* 통지 후 실제 사용 일수 - 정합성 검증 잡이 주기적으로 갱신 */
    @Column(name = "response_used_days", precision = 5, scale = 2)
    private BigDecimal responseUsedDays;

    /* 응답 기록 시각 - employeeResponse / responseUsedDays 갱신 시점 */
    @Column(name = "response_recorded_at")
    private LocalDateTime responseRecordedAt;


    /* 1차 통지 생성 - 사용 계획 제출 요구 */
    public static VacationPromotionNotice createFirst(VacationBalance balance, BigDecimal targetDays) {
        return baseBuilder(balance, targetDays, STAGE_FIRST).build();
    }

    /* 2차 통지 생성 - 사용 시기 지정 통보 */
    public static VacationPromotionNotice createSecond(VacationBalance balance, BigDecimal targetDays) {
        return baseBuilder(balance, targetDays, STAGE_SECOND).build();
    }

    /* 사원 응답 / 회사 지정 기록 - 추후 갱신 */
    public void recordResponse(String response, BigDecimal usedDays) {
        this.employeeResponse = response;
        this.responseUsedDays = usedDays;
        this.responseRecordedAt = LocalDateTime.now();
    }

    /* 공통 빌더 베이스 */
    private static VacationPromotionNoticeBuilder baseBuilder(VacationBalance balance,
                                                              BigDecimal targetDays, String stage) {
        return VacationPromotionNotice.builder()
                .companyId(balance.getCompanyId())
                .empId(balance.getEmployee().getEmpId())
                .vacationBalance(balance)
                .noticeYear(balance.getBalanceYear())
                .targetRemainingDays(targetDays)
                .noticeStage(stage)
                .noticeSentAt(LocalDateTime.now());
    }

    /* 1차 통지 여부 */
    public boolean isFirstNotice()  { return STAGE_FIRST.equals(noticeStage); }

    /* 2차 통지 여부 */
    public boolean isSecondNotice() { return STAGE_SECOND.equals(noticeStage); }
}