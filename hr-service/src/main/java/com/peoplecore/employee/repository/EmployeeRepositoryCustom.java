package com.peoplecore.employee.repository;

import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.EmpType;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.domain.EmployeeSortField;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


import java.util.UUID;

public interface EmployeeRepositoryCustom {
    Page<Employee>findAllWithFilter(UUID companyId, String keyword, Long deptId, EmpType empType, EmpStatus empStatus, EmployeeSortField sortField, Pageable pageable);

}
