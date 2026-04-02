package com.peoplecore.employee.repository;

import com.peoplecore.employee.domain.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByCompanyIdAndEmpEmail(UUID companyId, String empEmail);

    Optional<Employee> findByEmpNum(String empNum);

    boolean existsByCompanyIdAndEmpEmail(UUID companyId, String empEmail);

    boolean existsByEmpNum(String empNum);

    Optional<Employee> findByCompanyIdAndEmpNameAndEmpPhone(UUID companyId, String empName, String empPhone);

    Optional<Employee> findByEmpPhone(String empPhone);

    long countByCompanyIdAndDeptId(UUID companyId, Long deptId);

    @Query("SELECT e.deptId, COUNT(e) FROM Employee e WHERE e.companyId = :companyId GROUP BY e.deptId")
    List<Object[]> countByCompanyIdGroupByDeptId(UUID companyId);
}