package com.peoplecore.company.repository;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.domain.CompanyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CompanyRepository extends JpaRepository<Company, UUID> {

    boolean existsByCompanyIp(String companyIp);
    List<Company> findByCompanyStatus(CompanyStatus companyStatus);
}
