package com.peoplecore.vacation.service;

import com.peoplecore.company.domain.Company;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.vacation.dto.VacationAdvanceUsePolicyDto;
import com.peoplecore.vacation.dto.VacationGrantBasisDto;
import com.peoplecore.vacation.dto.VacationPromotionPolicyDto;
import com.peoplecore.vacation.dto.VacationRuleCreateRequest;
import com.peoplecore.vacation.dto.VacationRuleResponse;
import com.peoplecore.vacation.entity.VacationGrantRule;
import com.peoplecore.vacation.entity.VacationPolicy;
import com.peoplecore.vacation.repository.VacationGrantRuleRepository;
import com.peoplecore.vacation.repository.VacationPolicyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/* 연차 정책 / 발생 규칙 / 촉진 정책 서비스 */
@Service
@Slf4j
@Transactional(readOnly = true)
public class VacationPolicyService {

    private final VacationPolicyRepository vacationPolicyRepository;
    private final VacationGrantRuleRepository vacationGrantRuleRepository;
    private final StringRedisTemplate redisTemplate;

    /* initDefault 동시 호출 방지용 분산 락 - 회사 생성 Kafka 이벤트 중복 수신 대비 */
    private static final String INIT_LOCK_PREFIX = "vacation-policy-init";
    private static final Duration INIT_LOCK_TTL = Duration.ofSeconds(30);


    @Autowired
    public VacationPolicyService(VacationPolicyRepository vacationPolicyRepository,
                                 VacationGrantRuleRepository vacationGrantRuleRepository, StringRedisTemplate redisTemplate) {
        this.vacationPolicyRepository = vacationPolicyRepository;
        this.vacationGrantRuleRepository = vacationGrantRuleRepository;
        this.redisTemplate = redisTemplate;
    }

    /* 회사 생성 시 기본 정책 + 발생 규칙 11건 자동 INSERT (멱등) */
    @Transactional
    public void initDefault(Company company) {
        UUID companyId = company.getCompanyId();
        String lockKey = INIT_LOCK_PREFIX + ":" + companyId;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", INIT_LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            log.info("VacationPolicy 초기화 진행 중 - 다른 인스턴스 담당. companyId={}", companyId);
            return;
        }

        try {
            if (vacationPolicyRepository.existsByCompanyId(companyId)) {
                log.info("VacationPolicy 이미 존재 - companyId={}, 초기화 스킵", companyId);
                return;
            }
            VacationPolicy policy = VacationPolicy.createDefault(companyId, VacationPolicy.SYSTEM_EMP_ID);
            policy.getGrantRules().addAll(
                    VacationGrantRule.createCompanyDefaults(policy, VacationPolicy.SYSTEM_EMP_ID));
            vacationPolicyRepository.save(policy);
            log.info("VacationPolicy 기본 정책 + 규칙 {}건 생성 완료 - companyId={}",
                    policy.getGrantRules().size(), companyId);
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    /* 연차 지급 기준 조회 */
    public VacationGrantBasisDto getVacationGrantBasis(UUID companyId) {
        return VacationGrantBasisDto.from(findOrThrow(companyId));
    }

    /* 연차 지급 기준 변경 (HIRE/FISCAL 전환) - 상태 패턴 위임 */
    @Transactional
    public VacationGrantBasisDto updateVacationGrantBasis(UUID companyId, VacationGrantBasisDto dto) {
        VacationPolicy policy = findOrThrow(companyId);
        VacationPolicy.PolicyBaseType newBasis = VacationPolicy.PolicyBaseType.valueOf(dto.getGrantBasis());
        policy.changeGrantBasis(newBasis, dto.getFiscalYearStart());
        return VacationGrantBasisDto.from(policy);
    }

    /* 연차 발생 규칙 전체 조회 (정책 + 규칙 fetch join) */
    public List<VacationRuleResponse> getVacationRules(UUID companyId) {
        VacationPolicy policy = findOrThrowFetchRules(companyId);
        return policy.getGrantRules().stream()
                .map(VacationRuleResponse::from)
                .toList();
    }

    /* 연차 발생 규칙 추가 - cascade ALL 로 자식 INSERT */
    @Transactional
    public VacationRuleResponse createVacationRule(UUID companyId, Long empId,
                                                   VacationRuleCreateRequest request) {
        VacationPolicy policy = findOrThrow(companyId);
        validateNoOverlap(policy.getPolicyId(), request.getMinYears(), request.getMaxYears(), null);
        VacationGrantRule rule = VacationGrantRule.create(policy,
                request.getMinYears(), request.getMaxYears(),
                request.getDays(), request.getDesc(), empId);
        policy.getGrantRules().add(rule);
        return VacationRuleResponse.from(rule);
    }

    /* 연차 발생 규칙 수정 */
    @Transactional
    public VacationRuleResponse updateLeaveRule(UUID companyId,Long ruleId, VacationRuleCreateRequest request) {
        VacationGrantRule rule = loadRuleWithCompanyCheck(companyId, ruleId);
        /* 중첩 검증 - 자기 자신 제외하고 범위 겹치는 규칙 존재 시 예외 (MED [7]) */
        validateNoOverlap(rule.getVacationPolicy().getPolicyId(),
                request.getMinYears(), request.getMaxYears(), ruleId);
        rule.update(request.getMinYears(), request.getMaxYears(),
                request.getDays(), request.getDesc());
        return VacationRuleResponse.from(rule);
    }

    /* 연차 발생 규칙 삭제 */
    @Transactional
    public void deleteVacationRule(UUID companyId,Long ruleId) {
        VacationGrantRule rule = loadRuleWithCompanyCheck(companyId, ruleId);
        vacationGrantRuleRepository.delete(rule);
    }

    /* 연차 촉진 정책 조회 */
    public VacationPromotionPolicyDto getPromotionPolicy(UUID companyId) {
        return VacationPromotionPolicyDto.from(findOrThrow(companyId));
    }

    /* 연차 촉진 정책 변경 - DTO 받아 엔티티에 위임 */
    /* isActive=true 인데 firstMonthsBefore null 이면 엔티티 updatePromotionPolicy 가 VACATION_POLICY_FIRST_NOTICE_REQUIRED 예외 */
    @Transactional
    public void updatePromotionPolicy(UUID companyId, VacationPromotionPolicyDto dto) {
        VacationPolicy policy = findOrThrow(companyId);
        policy.updatePromotionPolicy(
                Boolean.TRUE.equals(dto.getIsActive()),
                dto.getFirstMonthsBefore(),
                dto.getSecondMonthsBefore()
        );
    }

    /* 미리쓰기 허용 정책 조회 */
    public VacationAdvanceUsePolicyDto getAdvanceUsePolicy(UUID companyId) {
        return VacationAdvanceUsePolicyDto.from(findOrThrow(companyId));
    }

    /* 미리쓰기 허용 정책 변경 - DTO 받아 엔티티에 위임 */
    /* isAllowed null 은 false 로 간주 (프론트 누락 방어) */
    @Transactional
    public void updateAdvanceUsePolicy(UUID companyId, VacationAdvanceUsePolicyDto dto) {
        VacationPolicy policy = findOrThrow(companyId);
        policy.updateAdvanceUse(Boolean.TRUE.equals(dto.getIsAllowed()));
    }

    /* 정책 단건 조회 (규칙 미포함) */
    private VacationPolicy findOrThrow(UUID companyId) {
        return vacationPolicyRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_POLICY_NOT_FOUND));
    }

    /* 정책 + 규칙 fetch join */
    private VacationPolicy findOrThrowFetchRules(UUID companyId) {
        return vacationPolicyRepository.findByCompanyIdFetchRules(companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_POLICY_NOT_FOUND));
    }

    /* ruleId 조회 + 정책의 companyId 가 요청 companyId 와 일치하는지 검증 */
    /* 다른 회사 ruleId 접근 시 NOT_FOUND 로 위장 (권한 누수 방지 - VacationTypeService 패턴 차용) */
    private VacationGrantRule loadRuleWithCompanyCheck(UUID companyId, Long ruleId) {
        VacationGrantRule rule = vacationGrantRuleRepository.findById(ruleId)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_RULE_NOT_FOUND));
        if (!rule.getVacationPolicy().getCompanyId().equals(companyId)) {
            log.warn("[VacationPolicy] 타 회사 ruleId 접근 차단 - companyId={}, ruleId={}, ruleCompanyId={}",
                    companyId, ruleId, rule.getVacationPolicy().getCompanyId());
            throw new CustomException(ErrorCode.VACATION_RULE_NOT_FOUND);
        }
        return rule;
    }

    /* 근속 구간 중첩 검증 - 새 규칙 [newMin, newMax) 가 기존 규칙 범위와 교집합 있으면 예외 */
    /* maxYear null = 무한대 (21년 이상 규칙). null 과 겹침 처리 주의 */
    private void validateNoOverlap(Long policyId, Integer newMin, Integer newMax, Long excludeRuleId) {
        if (newMin == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
        List<VacationGrantRule> existing = vacationGrantRuleRepository
                .findAllForOverlapCheck(policyId, excludeRuleId);
        for (VacationGrantRule r : existing) {
            if (rangesOverlap(newMin, newMax, r.getMinYear(), r.getMaxYear())) {
                log.warn("[VacationPolicy] 규칙 중첩 - policyId={}, new=[{},{}), existing=[{},{})",
                        policyId, newMin, newMax, r.getMinYear(), r.getMaxYear());
                throw new CustomException(ErrorCode.VACATION_RULE_OVERLAP);
            }
        }
    }

    /* 두 반열린 구간 [aMin, aMax), [bMin, bMax) 의 교집합 존재 여부 */
    /* max null = +∞ 로 간주. 예: [21, null) 과 [5, 7) → 겹침 없음 */
    private boolean rangesOverlap(Integer aMin, Integer aMax, Integer bMin, Integer bMax) {
        int aHi = (aMax != null) ? aMax : Integer.MAX_VALUE;
        int bHi = (bMax != null) ? bMax : Integer.MAX_VALUE;
        return aMin < bHi && bMin < aHi;
    }

}