package com.peoplecore.pay.repository;

import com.peoplecore.employee.domain.QEmployee;
import com.peoplecore.pay.domain.QRetirementPensionDeposits;
import com.peoplecore.pay.dtos.MonthlyDepositSummaryDto;
import com.peoplecore.pay.dtos.PensionDepositByEmployeeResDto;
import com.peoplecore.pay.dtos.PensionDepositResDto;
import com.peoplecore.pay.enums.DepStatus;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class PensionDepositQueryRepositoryImpl implements PensionDepositQueryRepository {

    private final JPAQueryFactory queryFactory;
    private final QRetirementPensionDeposits rpd = QRetirementPensionDeposits.retirementPensionDeposits;
    private QEmployee qEmp = QEmployee.employee;
    @Autowired
    public PensionDepositQueryRepositoryImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    @Override
    public Page<PensionDepositResDto> search(UUID companyId, String fromYm, String toYm, Long empId, Long deptId, DepStatus status, Pageable pageable) {
        return null;
    }

    @Override
    public Long sumDepositAmount(UUID companyId, String fromYm, String toYm, DepStatus status) {
        return 0L;
    }

    @Override
    public Integer countDistinctEmployees(UUID companyId, String fromYm, String toYm, DepStatus status) {
        return 0;
    }

    @Override
    public Long grandTotalDeposited(UUID companyId) {
        return 0L;
    }

    @Override
    public List<PensionDepositResDto> findByEmpId(UUID companyId, Long empId, String fromYm, String toYm) {
        return null;
    }

    @Override
    public List<MonthlyDepositSummaryDto> monthlySummary(UUID companyId, Integer year) {
        return null;
    }

    @Override
    public List<PensionDepositByEmployeeResDto> searchByEmployee(UUID companyId, String fromYm, String toYm, String search, Long deptId, DepStatus status) {
        return List.of();
    }
}
