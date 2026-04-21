package com.peoplecore.vacation.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/* 휴가 유형 - 회사별 마스터 (월차/연차/법정휴가/포상/여름휴가 등) */
/* 시스템 예약 코드는 StatutoryVacationType enum 에서 단일 관리 (변경/삭제 차단) */
@Entity
@Table(
        name = "vacation_type",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_vacation_type_company_code",
                columnNames = {"company_id", "type_code"}
        ),
        indexes = {
                @Index(name = "idx_vacation_type_company_active",
                        columnList = "company_id, is_active, sort_order")
        }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationType extends BaseTimeEntity {

    /* 스케줄러/배치에서 typeCode 직접 조회하는 호출부(MonthlyAccrualScheduler,           */
    /* AnnualTransitionScheduler, AnnualGrantScheduler, PromotionNoticeJobConfig,          */
    /* AnnualGrantFiscalJobConfig, LeaveAllowanceService) 호환을 위해 상수는 유지.         */
    /* 전체 예약 코드 목록은 StatutoryVacationType enum 참조.                              */
    public static final String CODE_MONTHLY = "MONTHLY";
    public static final String CODE_ANNUAL  = "ANNUAL";

    /* 휴가 유형 ID (PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "type_id")
    private Long typeId;

    /* 회사 ID */
    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    /* 회사 식별 코드 - "MONTHLY"/"ANNUAL"/"MATERNITY"/"SUMMER" 등. UNIQUE per 회사 */
    /* StatutoryVacationType enum 에 정의된 코드는 시스템 예약 - 변경/삭제 불가 */
    @Column(name = "type_code", nullable = false, length = 50)
    private String typeCode;

    /* 표시명 - 화면 노출용 ("월차", "연차", "출산전후휴가", "여름휴가") */
    @Column(name = "type_name", nullable = false, length = 100)
    private String typeName;

    /* 1회 신청 단위 - 1.0=종일 / 0.5=반차 / 0.25=반반차 */
    @Column(name = "deduct_unit", nullable = false, precision = 5, scale = 2)
    private BigDecimal deductUnit;

    /* 활성화 여부 - false 면 신규 신청 불가 (기존 잔여는 사용 가능) */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    /* 화면 정렬 순서 */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    /* 성별 제한 - 신청 시 사원 성별 대비 검증 (GenderLimit.allows() 호출) */
    /* 기본 ALL (성별 무관). 생리/출산/유산사산=FEMALE_ONLY, 배우자출산=MALE_ONLY */
    @Enumerated(EnumType.STRING)
    @Column(name = "gender_limit", nullable = false, length = 20)
    private GenderLimit genderLimit;

    /* 유급/무급 - 급여 계산(LeaveAllowanceService) 시 차감 근거 */
    /* 가족돌봄/생리만 UNPAID, 나머진 PAID */
    @Enumerated(EnumType.STRING)
    @Column(name = "pay_type", nullable = false, length = 10)
    private PayType payType;

    /* 활성화 토글 */
    public void activate()   { this.isActive = true; }
    public void deactivate() { this.isActive = false; }

    /* 표시 정보 변경 - 관리자 화면 수정 (typeCode 는 변경 불가) */
    public void updateDisplay(String typeName, BigDecimal deductUnit, Integer sortOrder) {
        this.typeName = typeName;
        this.deductUnit = deductUnit;
        this.sortOrder = sortOrder;
    }

    /* 시스템 예약 유형 여부 - StatutoryVacationType enum 에 정의된 코드면 수정/삭제 차단 */
    public boolean isSystemReserved() {
        return StatutoryVacationType.isReserved(this.typeCode);
    }

    /* 월차 유형 여부 - 스케줄러/배치 전용 (typeCode 동등 비교) */
    public boolean isMonthly() { return CODE_MONTHLY.equals(typeCode); }

    /* 연차 유형 여부 - 스케줄러/배치 전용 (typeCode 동등 비교) */
    public boolean isAnnual()  { return CODE_ANNUAL.equals(typeCode); }
}