package com.peoplecore.employee.repository;

import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.EmpType;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.domain.EmployeeSortField;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public interface EmployeeRepositoryCustom {
    // 목록 조회용 - 페이징
    Page<Employee> findAllWithFilter(UUID companyId, String keyword, Long deptId, EmpType empType, EmpStatus empStatus, EmployeeSortField sortField, Pageable pageable);

    // 급여계산용 - 전체 조회 (배치 처리)
    List<Employee> findAllForPayroll(Long companyId, YearMonth payMonth);


}
