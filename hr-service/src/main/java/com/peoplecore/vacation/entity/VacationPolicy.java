package com.peoplecore.vacation.entity;

import com.peoplecore.entity.BaseTimeEntity;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/* 회사별 휴가 정책 - 회사당 1건. 발생 기준(HIRE/FISCAL) + 연차 촉진 통지 정책 */
@Entity
@Table(
        name = "vacation_policy",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_vacation_policy_company",
                columnNames = "company_id"
        )
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationPolicy extends BaseTimeEntity {

    /* 회계연도 시작일 포맷 (mm-dd) */
    private static final Pattern FISCAL_DATE_PATTERN =
            Pattern.compile("^(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$");

    /* 연차 발생 기준 타입 - 상태 패턴으로 fiscalYearStart 검증 분기 */
    public enum PolicyBaseType {
        HIRE {
            /* 입사일 기준 - fiscalYearStart 무시 (null 저장) */
            @Override
            public String resolveFiscalStart(String input) {
                return null;
            }
        },
        FISCAL {
            /* 회계연도 기준 - fiscalYearStart 필수 (mm-dd 형식 검증) */
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

        public abstract String resolveFiscalStart(String input);
    }

    /* 연차 정책 ID (PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "policy_id")
    private Long policyId;

    /* 회사 ID - UNIQUE (회사당 1건) */
    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    /* 정책 생성/수정자 사원 ID. 시스템 자동 생성 시 0L */
    @Column(name = "policy_emp_id", nullable = false)
    private Long policyEmpId;

    /* 연차 발생 기준 - HIRE(입사일) / FISCAL(회계연도) */
    @Enumerated(EnumType.STRING)
    @Column(name = "policy_base_type", nullable = false, length = 20)
    private PolicyBaseType policyBaseType;

    /* 회계연도 시작일 (mm-dd) - FISCAL 일 때만 값. HIRE 면 null */
    @Column(name = "policy_fiscal_year_start", length = 5)
    private String policyFiscalYearStart;

    /* 연차 촉진 전체 사용 여부 - 화면 상단 토글. false 면 1차/2차 무시하고 미사용=수당 */
    @Column(name = "is_promotion_active", nullable = false)
    @Builder.Default
    private Boolean isPromotionActive = false;

    /* 1차 촉진 통지 시기 - 만료 N개월 전. NULL = 1차 비활성. 화면 셀렉트 (3/4/5/6/7개월) */
    @Column(name = "first_notice_months_before")
    private Integer firstNoticeMonthsBefore;

    /* 2차 촉진 통지 시기 - 만료 N개월 전. NULL = 2차 비활성. 화면 셀렉트 (1/2/3개월) */
    @Column(name = "second_notice_months_before")
    private Integer secondNoticeMonthsBefore;

    /* 연월차 미리쓰기 허용 여부 -> true면 잔여 부족해도 신청 가능 스케줄러로 연차 생성 시 해당년도 availible이 음수면 그만큼 차감햇서 적립
     * 법정 휴가는 아님 */
    @Column(name = "allow_advance_use", nullable = false)
    @Builder.Default
    private Boolean allowAdvanceUse = false;

    /* 낙관적 락 - 관리자 동시 수정 방지 */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /* 연차 발생 규칙 목록 - cascade ALL 로 함께 INSERT */
    @OneToMany(mappedBy = "vacationPolicy", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<VacationGrantRule> grantRules = new ArrayList<>();


    /* 발생 기준 변경 (상태 패턴) - HIRE/FISCAL 별 검증 위임 */
    public void changeGrantBasis(PolicyBaseType newBasis, String fiscalYearStartInput) {
        this.policyBaseType = newBasis;
        this.policyFiscalYearStart = newBasis.resolveFiscalStart(fiscalYearStartInput);
    }

    /* 연차 촉진 정책 변경 - 화면 저장 시 호출 */
    /* isActive=false 면 1차/2차 모두 NULL 처리 */
    /* isActive=true 면 1차 필수, 2차 선택. 2차는 1차보다 작아야 (만료에 더 가까워야) 함 */
    public void updatePromotionPolicy(boolean isActive,
                                      Integer firstMonthsBefore,
                                      Integer secondMonthsBefore) {
        if (!isActive) {
            this.isPromotionActive = false;
            this.firstNoticeMonthsBefore = null;
            this.secondNoticeMonthsBefore = null;
            return;
        }
        if (firstMonthsBefore == null) {
            throw new CustomException(ErrorCode.VACATION_POLICY_FIRST_NOTICE_REQUIRED);
        }
        // 2차는 만료에 더 가까운 통지라 1차보다 개월수가 작아야 함. 같거나 크면 의미 없음
        if (secondMonthsBefore != null && secondMonthsBefore >= firstMonthsBefore) {
            throw new CustomException(ErrorCode.VACATION_POLICY_NOTICE_ORDER_INVALID);
        }
        this.isPromotionActive = true;
        this.firstNoticeMonthsBefore = firstMonthsBefore;
        this.secondNoticeMonthsBefore = secondMonthsBefore;
    }

    /* 미리쓰기 허용 토글 변경 - 관리자 화면 저장 시 호출 */
    public void updateAdvanceUse(boolean allowed) {
        this.allowAdvanceUse = allowed;
    }

    /* 미리쓰기 활성 여부 - VacationRequestService.markPending / AnnualGrantService.grantAndRecord 참조 */
    public boolean isAdvanceUseActive() {
        return Boolean.TRUE.equals(allowAdvanceUse);
    }

    /* 1차 촉진 활성 여부 - 컬럼이 NULL 이 아니고 전체 토글이 ON 일 때만 */
    public boolean isFirstNoticeActive() {
        return Boolean.TRUE.equals(isPromotionActive) && firstNoticeMonthsBefore != null;
    }

    /* 2차 촉진 활성 여부 - 1차 활성 전제 + 2차 컬럼 NULL 아님 */
    public boolean isSecondNoticeActive() {
        return isFirstNoticeActive() && secondNoticeMonthsBefore != null;
    }

    /* 미사용 연차 수당 면제 조건 - LeaveAllowanceService 참조용 */
    /* 1차+2차 모두 활성 + 실제 통지 이력 존재 시 면제 (이력 검증은 호출부) */
    public boolean isBuyoutExempted() {
        return isFirstNoticeActive() && isSecondNoticeActive();
    }

    /* 회사 생성 시 기본 정책 - HIRE 기준, 촉진 비활성, 규칙 11건 부착은 호출부에서 */
    /* HIRE 기준, 촉진 비활성, 규칙 부착은 호출부가 별도 처리 */
    public static VacationPolicy createDefault(UUID companyId, Long creatorEmpId) {
        Long effectiveCreator = (creatorEmpId != null) ? creatorEmpId : SYSTEM_EMP_ID;
        return VacationPolicy.builder()
                .companyId(companyId)
                .policyEmpId(effectiveCreator)
                .policyBaseType(PolicyBaseType.HIRE)
                .policyFiscalYearStart(null)
                .isPromotionActive(false)
                .firstNoticeMonthsBefore(null)
                .secondNoticeMonthsBefore(null)
                .allowAdvanceUse(false)
                .build();
    }

    /* 시스템 자동 생성 유저 ID - initDefault / createCompanyDefaults 등에서 사용 */
    /* 실제 사원 ID(양수) 와 충돌 방지를 위해 음수 예약 */
    public static final Long SYSTEM_EMP_ID = -1L;


}