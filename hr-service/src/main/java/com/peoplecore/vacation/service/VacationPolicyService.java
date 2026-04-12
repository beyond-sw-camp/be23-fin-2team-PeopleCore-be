package com.peoplecore.vacation.service;

import com.peoplecore.company.domain.Company;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.vacation.dto.VacationGrantBasisDto;
import com.peoplecore.vacation.dto.VacationRuleCreateRequest;
import com.peoplecore.vacation.dto.VacationRuleResponse;
import com.peoplecore.vacation.entity.VacationCreateRule;
import com.peoplecore.vacation.entity.VacationPolicy;
import com.peoplecore.vacation.repository.VacationCreateRuleRepository;
import com.peoplecore.vacation.repository.VacationPolicyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 연차 정책 / 발생 규칙 서비스

 */
@Service
@Slf4j
@Transactional(readOnly = true)
public class VacationPolicyService {

    private final VacationPolicyRepository vacationPolicyRepository;
    private final VacationCreateRuleRepository vacationCreateRuleRepository;

    @Autowired
    public VacationPolicyService(VacationPolicyRepository vacationPolicyRepository,
                                 VacationCreateRuleRepository vacationCreateRuleRepository) {
        this.vacationPolicyRepository = vacationPolicyRepository;
        this.vacationCreateRuleRepository = vacationCreateRuleRepository;
    }

    /* ========== 회사 생성 시 기본 정책 초기화 ========== */

    /**
     * 회사 생성 시 기본 연차 정책 1건 자동 생성 (멱등)
     */
    @Transactional
    public void initDefault(Company company) {
        if (vacationPolicyRepository.existsByCompanyId(company.getCompanyId())) {
            log.info("VacationPolicy 이미 존재 - companyId={}, 초기화 스킵", company.getCompanyId());
            return;
        }
        vacationPolicyRepository.save(VacationPolicy.createDefault(company.getCompanyId(), 0L));
        log.info("VacationPolicy 기본 정책 생성 완료 - companyId={}", company.getCompanyId());
    }

    /* ========== 연차 지급 기준 ========== */

    /**
     * 연차 지급 기준 조회
     */
    public VacationGrantBasisDto getVacationGrantBasis(UUID companyId) {
        return VacationGrantBasisDto.from(findSingleOrThrow(companyId));
    }

    /**
     * 연차 지급 기준 변경 (상태 패턴)
     */
    @Transactional
    public VacationGrantBasisDto updateVacationGrantBasis(UUID companyId, VacationGrantBasisDto dto) {
        VacationPolicy policy = findSingleOrThrow(companyId);
        VacationPolicy.PolicyBaseType newBasis = VacationPolicy.PolicyBaseType.valueOf(dto.getGrantBasis());
        policy.changeGrantBasis(newBasis, dto.getFiscalYearStart());
        return VacationGrantBasisDto.from(policy);
    }

    /* ========== 연차 발생 규칙 ========== */

    /**
     * 연차 발생 규칙 전체 조회
     */
    public List<VacationRuleResponse> getVacationRules(UUID companyId) {
        VacationPolicy policy = findSingleOrThrowWithRules(companyId);
        return policy.getCreateRules().stream()
                .map(VacationRuleResponse::from)
                .toList();
    }

    /**
     * 연차 발생 규칙 추가

     */
    @Transactional
    public VacationRuleResponse createVacationRule(UUID companyId, Long empId, VacationRuleCreateRequest request) {
        VacationPolicy policy = findSingleOrThrow(companyId);
        VacationCreateRule rule = VacationCreateRule.create(policy,
                request.getMinYears(), request.getMaxYears(),
                request.getDays(), request.getDesc(), empId);
        policy.getCreateRules().add(rule);
        return VacationRuleResponse.from(rule);
    }

    /**
     * 연차 발생 규칙 수정
     */
    @Transactional
    public VacationRuleResponse updateLeaveRule(Long ruleId, VacationRuleCreateRequest request) {
        VacationCreateRule rule = vacationCreateRuleRepository.findById(ruleId)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_RULE_NOT_FOUND));
        rule.update(request.getMinYears(), request.getMaxYears(), request.getDays(), request.getDesc());
        return VacationRuleResponse.from(rule);
    }

    /**
     * 연차 발생 규칙 삭제

     */
    @Transactional
    public void deleteVacationRule(Long ruleId) {
        if (!vacationCreateRuleRepository.existsById(ruleId)) {
            throw new CustomException(ErrorCode.VACATION_RULE_NOT_FOUND);
        }
        vacationCreateRuleRepository.deleteById(ruleId);
    }

    /* ========== 내부 유틸 ========== */

    /**
     * 정책 단건 조회 (규칙 미포함)
     * - 규칙이 필요 없는 지급 기준 조회/수정용
     */
    private VacationPolicy findSingleOrThrow(UUID companyId) {
        return reduceToSingle(vacationPolicyRepository.findAllByCompanyId(companyId), companyId);
    }

    /**
     * 정책 단건 조회
     */
    private VacationPolicy findSingleOrThrowWithRules(UUID companyId) {
        return reduceToSingle(vacationPolicyRepository.findAllByCompanyIdFetchRules(companyId), companyId);
    }

    /**
     * 정책 리스트 → 단건 검증/반환 공통 로직
     */
    private VacationPolicy reduceToSingle(List<VacationPolicy> policies, UUID companyId) {
        if (policies.isEmpty()) {
            throw new CustomException(ErrorCode.VACATION_POLICY_NOT_FOUND);
        }
        if (policies.size() > 1) {
            log.error("VacationPolicy 중복 탐지 - companyId={}, count={}", companyId, policies.size());
            throw new CustomException(ErrorCode.VACATION_POLICY_DUPLICATED);
        }
        return policies.get(0);
    }
}
