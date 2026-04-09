package com.peoplecore.vacation.service;

import com.peoplecore.vacation.dto.VacationGrantBasisDto;
import com.peoplecore.vacation.dto.VacationRuleCreateRequest;
import com.peoplecore.vacation.dto.VacationRuleResponse;
import com.peoplecore.vacation.entity.VacationCreateRule;
import com.peoplecore.vacation.entity.VacationPolicy;
import com.peoplecore.vacation.repository.VacationCreateRuleRepository;
import com.peoplecore.vacation.repository.VacationPolicyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class VacationPolicyService {
    private final VacationPolicyRepository vacationPolicyRepository;
    private final VacationCreateRuleRepository vacationCreateRuleRepository;

    @Autowired
    public VacationPolicyService(VacationPolicyRepository vacationPolicyRepository, VacationCreateRuleRepository vacationCreateRuleRepository) {
        this.vacationPolicyRepository = vacationPolicyRepository;
        this.vacationCreateRuleRepository = vacationCreateRuleRepository;
    }


    /* =============== 연차 지급 기준 ======================*/
    /*연차 지급 기준 조회 -> 회사 Id로 연차 정책을 찾아서 지급 기준 반환*/
    public VacationGrantBasisDto getVacationGrantBasis(UUID companyId) {
        VacationPolicy policy = vacationPolicyRepository.findByCompanyId(companyId).orElseThrow(() -> new IllegalArgumentException("연차 정책이 존재하지 않습니다, "));
        return VacationGrantBasisDto.from(policy);
    }

    /*연차 지급 기준 변경 */
    @Transactional
    public VacationGrantBasisDto updateVacationGrantBasis(UUID companyId, VacationGrantBasisDto dto) {
        /*회사 Id로 기존 정책 조회*/
        VacationPolicy policy = vacationPolicyRepository.findByCompanyId(companyId).orElseThrow(() -> new IllegalArgumentException("연차 정책이 존재하지 않습니다, "));

        /*엔티티 필드 변경 */
        policy.changeGrantBasis(VacationPolicy.PolicyBaseType.valueOf(dto.getGrantBasis()));
        return VacationGrantBasisDto.from(policy);
    }

    /* ============ 연차 발생 규칙 ==============*/
    /* 연차 발생 규칙 전체 조회 */
    public List<VacationRuleResponse> getVacationRules(UUID companyId) {
        VacationPolicy policy = findPolicyByCompanyId(companyId);
        return policy.getCreateRules().stream().map(VacationRuleResponse::from).toList();
    }

    /*연차 발생 규칙 추가 */
    @Transactional
    public VacationRuleResponse createVacationRule(UUID companyId, Long empId, VacationRuleCreateRequest request) {
        VacationPolicy policy = findPolicyByCompanyId(companyId);
        VacationCreateRule rule = VacationCreateRule.create(policy, request.getMinYears(), request.getMaxYears(), request.getDays(), request.getDesc(), empId);
        policy.getCreateRules().add(rule);
        return VacationRuleResponse.from(rule);
    }

    /* 연차 발생 규칙 수정 */
    @Transactional
    public VacationRuleResponse updateLeaveRule(Long ruleId, VacationRuleCreateRequest request) {
        VacationCreateRule rule = vacationCreateRuleRepository.findById(ruleId).orElseThrow(() -> new IllegalArgumentException("연차 발생 규칙이 존재하지 않습니다, "));
        rule.update(request.getMinYears(), request.getMaxYears(), request.getDays(), request.getDesc());
        return VacationRuleResponse.from(rule);
    }

    /* 연차 발생 규칙 삭제 */
    @Transactional
    public void deleteVacationRule(Long ruleId) {
        if (!vacationCreateRuleRepository.existsById(ruleId)) {
            throw new IllegalArgumentException("연차 발생 규칙이 존재하지 않습니다. ");
        }
        vacationCreateRuleRepository.deleteById(ruleId);
    }


    /* 회사 Id로 정책 조회  */
    private VacationPolicy findPolicyByCompanyId(UUID companyId) {
        return vacationPolicyRepository.findByCompanyId(companyId).orElseThrow(() -> new IllegalArgumentException("연차 정책이 존재하지 않습니다."));
    }
}
