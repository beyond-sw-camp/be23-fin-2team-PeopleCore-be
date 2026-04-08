package com.peoplecore.salarycontract.repository;


import com.peoplecore.salarycontract.domain.SalaryContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface SalaryContractRepository extends JpaRepository<SalaryContract, Long>, SalaryContractRepositoryCustom {


}
