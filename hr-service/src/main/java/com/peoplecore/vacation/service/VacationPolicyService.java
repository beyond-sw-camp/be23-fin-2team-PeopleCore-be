package com.peoplecore.vacation.service;

import com.peoplecore.company.domain.Company;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/* 연차 정책 / 발생 규칙 / 촉진 정책 서비스 */
@Service
@Slf4j
@Transactional(readOnly = true)
public class VacationPolicyService {

    private final VacationPolicyRepository vacationPolicyRepository;
    private final VacationGrantRuleRepository vacationGrantRuleRepository;

    @Autowired
    public VacationPolicyService(VacationPolicyRepository vacationPolicyRepository,
                                 VacationGrantRuleRepository vacationGrantRuleRepository) {
        this.vacationPolicyRepository = vacationPolicyRepository;
        this.vacationGrantRuleRepository = vacationGrantRuleRepository;
    }

    /* 회사 생성 시 기본 정책 + 발생 규칙 11건 자동 INSERT (멱등) */
    @Transactional
    public void initDefault(Company company) {
        UUID companyId = company.getCompanyId();
        if (vacationPolicyRepository.existsByCompanyId(companyId)) {
            log.info("VacationPolicy 이미 존재 - companyId={}, 초기화 스킵", companyId);
            return;
        }
        VacationPolicy policy = VacationPolicy.createDefault(companyId, 0L);
        policy.getGrantRules().addAll(VacationGrantRule.createCompanyDefaults(policy, 0L));
        vacationPolicyRepository.save(policy);
        log.info("VacationPolicy 기본 정책 + 규칙 {}건 생성 완료 - companyId={}",
                policy.getGrantRules().size(), companyId);
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
        VacationGrantRule rule = VacationGrantRule.create(policy,
                request.getMinYears(), request.getMaxYears(),
                request.getDays(), request.getDesc(), empId);
        policy.getGrantRules().add(rule);
        return VacationRuleResponse.from(rule);
    }

    /* 연차 발생 규칙 수정 */
    @Transactional
    public VacationRuleResponse updateLeaveRule(Long ruleId, VacationRuleCreateRequest request) {
        VacationGrantRule rule = vacationGrantRuleRepository.findById(ruleId)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_RULE_NOT_FOUND));
        rule.update(request.getMinYears(), request.getMaxYears(),
                request.getDays(), request.getDesc());
        return VacationRuleResponse.from(rule);
    }

    /* 연차 발생 규칙 삭제 */
    @Transactional
    public void deleteVacationRule(Long ruleId) {
        if (!vacationGrantRuleRepository.existsById(ruleId)) {
            throw new CustomException(ErrorCode.VACATION_RULE_NOT_FOUND);
        }
        vacationGrantRuleRepository.deleteById(ruleId);
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
}