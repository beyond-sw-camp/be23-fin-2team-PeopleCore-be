package com.peoplecore.attendance.service;

import com.peoplecore.attendance.dto.OverTimePolicyReqDto;
import com.peoplecore.attendance.dto.OverTimePolicyResDto;
import com.peoplecore.attendance.entity.OvertimePolicy;
import com.peoplecore.attendance.repository.OverTimePolicyRepository;
import com.peoplecore.company.domain.Company;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
@Slf4j
public class OverTimePolicyService {

    private final OverTimePolicyRepository overTimePolicyRepository;
    private final CompanyRepository companyRepository;

    @Autowired
    public OverTimePolicyService(OverTimePolicyRepository overTimePolicyRepository, CompanyRepository companyRepository) {
        this.overTimePolicyRepository = overTimePolicyRepository;
        this.companyRepository = companyRepository;
    }


    /*정책 조회*/
    @Transactional(readOnly = true)
    public OverTimePolicyResDto getOverTimePolicy(UUID companyId) {
        return overTimePolicyRepository.findByCompany_CompanyId(companyId).map(OverTimePolicyResDto::from).orElse(OverTimePolicyResDto.defaultPolicy());
    }


    /*정책 생성/수정*/
    public OverTimePolicyResDto createOverTimePolicy(UUID companyId, Long empId, String empName, OverTimePolicyReqDto dto) {
        OvertimePolicy policy = overTimePolicyRepository.findByCompany_CompanyId(companyId).map(otPolicy -> {
            otPolicy.update(dto, empId, empName);
            return otPolicy;
        }).orElseGet(() -> {
            Company company = companyRepository.findById(companyId).orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND));
            return OvertimePolicy.builder()
                    .company(company)
                    .otMinUnit(dto.getOtMinUnit())
                    .otPolicyWeeklyMaxMinutes(dto.getOtPolicyWeeklyMaxMinutes())
                    .otPolicyWarningMinutes(dto.getOtPolicyWarningMinutes())
                    .otExceedAction(dto.getOtExceedAction())
                    .otPolicyManagerId(empId)
                    .otPolicyManagerName(empName)
                    .build();
        });

        overTimePolicyRepository.save(policy);
        return OverTimePolicyResDto.from(policy);

    }


    /* 회사 생성 시점 기본 초과근무 정책 1 row INSERT
     * - OvertimePolicy @Builder.Default 값 적용 (15분 / 3120분 / 2700분 / NOTIFY)
     * - 관리자 id/이름은 시스템 생성이므로 null 허용
     * - CompanyService.createCompany 에서 호출됨
     */
    public void initDefault(Company company) {
        OvertimePolicy policy = OvertimePolicy.builder()
                .company(company)
                .build();
        overTimePolicyRepository.save(policy);
    }

}
