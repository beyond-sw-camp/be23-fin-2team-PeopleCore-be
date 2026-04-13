package com.peoplecore.company.repository;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.domain.CompanyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CompanyRepository extends JpaRepository<Company, UUID> {
    /** 상태별 회사 목록 조회 (예: ACTIVE 회사만 조회) */
    List<Company> findByCompanyStatus(CompanyStatus companyStatus);
}
