package com.peoplecore.vacation.entity;

import com.peoplecore.entity.BaseTimeEntity;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import lombok.*;

/**
 * 연차 정책
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
        uniqueConstraints = @UniqueConstraint(
                name = "uk_vacation_policy_company",
                columnNames = "company_id"
        )
)
public class VacationPolicy extends BaseTimeEntity {
    /** 회계연도 시작일 포맷 (mm-dd, 01-01 ~ 12-31) */
    private static final Pattern FISCAL_DATE_PATTERN =
            Pattern.compile("^(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$");

    /*연차 지급 기준 타입 */
    public enum PolicyBaseType {
        HIRE { //입사일 기준 타입

            @Override
            public String resolveFiscalStart(String input) {
                return null;
            }
        },
        FISCAL { //회계연도 기준

            @Override
            public String resolveFiscalStart(String input) {
                if (input == null || input.isBlank()) {
                    throw new CustomException(ErrorCode.VACATION_POLICY_FISCAL_START_REQUIRED);
                }
                if (!FISCAL_DATE_PATTERN.matcher(input).matches()) {
                    throw new CustomException(ErrorCode.VACATION_POLICY_FISCAL_START_INVALID);
                }
                return input;
            }
        };

        /**
         * 상태별 fiscalYearStart 검증/정규화
         */
        public abstract String resolveFiscalStart(String input);
    }

    /**
     * 연차 정책 Id
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long policyId;

    /**
     * 회사 ID
     */
    @Column(nullable = false, name = "company_id")
    private UUID companyId;

    /**
     * 권한 정책 생성 사원 id
     */
    @Column(nullable = false)
    private Long policyEmpId;


    /**
     * 연차 지급 기준
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PolicyBaseType policyBaseType;

    /**
     * 회계연도 시작일 - mm-dd
     */
    private String policyFiscalYearStart;

    /**
     * 연차 촉진 알람 잔여일
     */
    @Column(nullable = false)
    private String policyAlarmDay;

    /**
     * 낙관적 락 버전
     * - 관리자 2명 동시 수정 시 마지막 커밋자가 OptimisticLockException 받고 재조회 강제
     */
    @Version
    private Long version;


    /**
     * 연차 발생 규칙 목록 (양방향)
     */
    @OneToMany(mappedBy = "vacationPolicy", cascade = CascadeType.ALL)
    @Builder.Default
    private List<VacationCreateRule> createRules = new ArrayList<>();

    public void changeGrantBasis(PolicyBaseType newBasis, String fiscalYearStartInput) {
        this.policyBaseType = newBasis;
        this.policyFiscalYearStart = newBasis.resolveFiscalStart(fiscalYearStartInput);
    }
    public static VacationPolicy createDefault(UUID companyId, Long creatorEmpId) {
        return VacationPolicy.builder()
                .companyId(companyId)
                .policyEmpId(creatorEmpId)
                .policyBaseType(PolicyBaseType.HIRE)
                .policyFiscalYearStart(null)
                .policyAlarmDay("30")
                .build();
    }

}
