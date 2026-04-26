package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.InsuranceJobTypes;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InsuranceJobTypesRepository extends JpaRepository<InsuranceJobTypes, Long> {

    Optional<InsuranceJobTypes> findByCompany_CompanyIdAndJobTypeName(UUID companyId, String name);

//    회사 전체 업종 목록
    List<InsuranceJobTypes> findByCompany_CompanyIdOrderByJobTypesIdAsc(UUID companyId);

//    회사 활성 업종 목록
    List<InsuranceJobTypes> findByCompany_CompanyIdAndIsActiveTrueOrderByJobTypesIdAsc(UUID companyId);

//    특정 업종 (회사검증포함) /??
    Optional<InsuranceJobTypes> findByJobTypesIdAndCompany_CompanyId(Long jobTypeId, UUID companyId);

}
