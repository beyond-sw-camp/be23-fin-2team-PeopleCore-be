package com.peoplecore.salarycontract.repository;


import com.peoplecore.salarycontract.domain.SalaryContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;


@Repository
public interface SalaryContractRepository extends JpaRepository<SalaryContract, Long>, SalaryContractRepositoryCustom {

    List<SalaryContract>findByCompanyIdAndEmployee_EmpIdAndDeletedAtIsNullOrderByContractYearDesc(UUID companyId, Long empId);

}
