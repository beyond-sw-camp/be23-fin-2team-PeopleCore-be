package com.peoplecore.salarycontract.repository;


import com.peoplecore.salarycontract.domain.SalaryContractSortField;
import com.peoplecore.salarycontract.dto.SalaryContractListResDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SalaryContractRepositoryCustom {
    Page<SalaryContractListResDto> findAllWithFilter(UUID companyId, String search, String year, SalaryContractSortField sortField, Pageable pageable);

}
