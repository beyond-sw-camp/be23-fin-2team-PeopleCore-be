package com.peoplecore.employee.repository;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.grade.domain.Grade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByCompany_CompanyIdAndEmpEmail(UUID companyId, String empEmail);

    Optional<Employee> findByEmpNum(String empNum);

    boolean existsByCompany_CompanyIdAndEmpEmail(UUID companyId, String empEmail);

    boolean existsByEmpNum(String empNum);

    Optional<Employee> findByCompany_CompanyIdAndEmpNameAndEmpPhone(UUID companyId, String empName, String empPhone);

    Optional<Employee> findByEmpPhone(String empPhone);

    long countByCompany_CompanyIdAndDepartment_Id(UUID companyId, Long deptId);

    @Query("""
        SELECT e.department.id, COUNT(e)
        FROM Employee e
        WHERE e.company.companyId = :companyId
        GROUP BY e.department.id
    """)
    List<Object[]> countByCompanyIdGroupByDeptId(UUID companyId);

    boolean existsByGrade(Grade grade);
}