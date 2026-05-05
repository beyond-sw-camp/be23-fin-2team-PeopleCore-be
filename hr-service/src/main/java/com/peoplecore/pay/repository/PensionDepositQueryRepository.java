package com.peoplecore.pay.repository;

import com.peoplecore.pay.dtos.MonthlyDepositSummaryDto;
import com.peoplecore.pay.dtos.PensionDepositByEmployeeResDto;
import com.peoplecore.pay.dtos.PensionDepositResDto;
import com.peoplecore.pay.enums.DepStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface PensionDepositQueryRepository {

    Page<PensionDepositResDto> search(UUID companyId, String fromYm, String toYm,
                                      Long empId, Long deptId, DepStatus status,
                                      Pageable pageable);

    Long sumDepositAmount(UUID companyId, String fromYm, String toYm, DepStatus status);

    Integer countDistinctEmployees(UUID companyId, String fromYm, String toYm, DepStatus status);

    Long grandTotalDeposited(UUID companyId);

    List<PensionDepositResDto> findByEmpId(UUID companyId, Long empId, String fromYm, String toYm);

    List<MonthlyDepositSummaryDto> monthlySummary(UUID companyId, Integer year);

    List<PensionDepositByEmployeeResDto> searchByEmployee(
            UUID companyId, String fromYm, String toYm,
            String search, Long deptId, DepStatus status);

    //    적립예정(SCHEDULED) 상태인 distinct payYearMonth 목록 (오름차순)
    List<String> distinctScheduledMonths(UUID companyId, String fromYm, String toYm);
}
