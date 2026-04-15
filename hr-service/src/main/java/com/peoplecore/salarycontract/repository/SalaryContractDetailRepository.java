package com.peoplecore.salarycontract.repository;

import com.peoplecore.salarycontract.domain.SalaryContractDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalaryContractDetailRepository extends JpaRepository<SalaryContractDetail, Long> {

    boolean existsByPayItemId(Long payItemId);

    List<SalaryContractDetail> findByContract_ContractId(Long contractId);

}
