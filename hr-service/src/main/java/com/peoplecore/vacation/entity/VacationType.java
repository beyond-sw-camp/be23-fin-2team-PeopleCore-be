package com.peoplecore.vacation.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/* 휴가 유형 - 회사별 마스터 (월차/연차/포상/여름휴가 등) */
/* 시스템 자동 처리 식별은 type_code 로 (MONTHLY/ANNUAL 예약). 그 외는 모두 관리자 부여 */
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

    /* 시스템 예약 type_code 상수 - 회사 생성 시 자동 INSERT, 변경/삭제 차단 */
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

    /* 회사 식별 코드 - "MONTHLY"/"ANNUAL"/"SUMMER" 등. UNIQUE per 회사 */
    /* MONTHLY/ANNUAL 은 시스템 예약 - 변경/삭제 불가 (서비스 레이어에서 검증) */
    @Column(name = "type_code", nullable = false, length = 50)
    private String typeCode;

    /* 표시명 - 화면 노출용 ("월차", "연차", "여름휴가") */
    @Column(name = "type_name", nullable = false, length = 100)
    private String typeName;

    /* 1회 신청 단위 - 1.0=종일 / 0.5=반차 / 0.25=반반차 */
    /* 신청 화면에서 사원이 선택할 수 있는 최소 단위 표시용 */
    @Column(name = "deduct_unit", nullable = false, precision = 5, scale = 2)
    private BigDecimal deductUnit;

    /* 활성화 여부 - false 면 신규 신청 불가 (기존 잔여는 사용 가능) */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    /* 화면 정렬 순서 */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;


    /* 회사 생성 시 자동 INSERT - 월차 (시스템 예약) */
    public static VacationType createDefaultMonthly(UUID companyId) {
        return VacationType.builder()
                .companyId(companyId)
                .typeCode(CODE_MONTHLY)
                .typeName("월차")
                .deductUnit(BigDecimal.ONE)
                .isActive(true)
                .sortOrder(1)
                .build();
    }

    /* 회사 생성 시 자동 INSERT - 연차 (시스템 예약) */
    public static VacationType createDefaultAnnual(UUID companyId) {
        return VacationType.builder()
                .companyId(companyId)
                .typeCode(CODE_ANNUAL)
                .typeName("연차")
                .deductUnit(new BigDecimal("0.25"))
                .isActive(true)
                .sortOrder(2)
                .build();
    }

    /* 활성화 토글 */
    public void activate()   { this.isActive = true; }
    public void deactivate() { this.isActive = false; }

    /* 표시 정보 변경 - 관리자 화면 수정 (typeCode 는 변경 불가) */
    public void updateDisplay(String typeName, BigDecimal deductUnit, Integer sortOrder) {
        this.typeName = typeName;
        this.deductUnit = deductUnit;
        this.sortOrder = sortOrder;
    }

    /* 시스템 예약 유형 여부 - typeCode 가 MONTHLY/ANNUAL 이면 변경/삭제 차단 대상 */
    /* 스케줄러가 월차/연차 잡 돌릴 때도 이 메서드 / typeCode 직접 조회 */
    public boolean isSystemReserved() {
        return CODE_MONTHLY.equals(typeCode) || CODE_ANNUAL.equals(typeCode);
    }

    /* 월차 유형 여부 */
    public boolean isMonthly() { return CODE_MONTHLY.equals(typeCode); }

    /* 연차 유형 여부 */
    public boolean isAnnual()  { return CODE_ANNUAL.equals(typeCode); }
}