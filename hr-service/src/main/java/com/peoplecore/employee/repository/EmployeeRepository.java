package com.peoplecore.employee.repository;

import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.grade.domain.Grade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, EmployeeRepositoryCustom {

    Optional<Employee> findByCompanyIdAndEmpEmail(UUID companyId, String empEmail);
    Optional<Employee> findByCompany_CompanyIdAndEmpEmail(UUID companyId, String empEmail);

    Optional<Employee> findByEmpNum(String empNum);

    boolean existsByCompanyIdAndEmpEmail(UUID companyId, String empEmail);
    boolean existsByCompany_CompanyIdAndEmpEmail(UUID companyId, String empEmail);

    boolean existsByEmpNum(String empNum);

    Optional<Employee> findByCompanyIdAndEmpNameAndEmpPhone(UUID companyId, String empName, String empPhone);
    Optional<Employee> findByCompany_CompanyIdAndEmpNameAndEmpPhone(UUID companyId, String empName, String empPhone);

    Optional<Employee> findByEmpPhone(String empPhone);

    long countByCompanyIdAndDeptId(UUID companyId, Long deptId);

    //  카드조회용
    long countCompany_CompanyIdAndEmpStatusNot(UUID companyId, EmpStatus status);

    long countCompany_CompanyIdAndEmpStatus(UUID companyId, EmpStatus status);
    long countByCompany_CompanyIdAndDepartment_Id(UUID companyId, Long deptId);

    @Query("""
            SELECT COUNT(e) FROM Employee e
            WHERE e.company.companyId = :companyId
            AND YEAR(e.empHireDate) = :year
            AND MONTH(e.empHireDate) = :month
            AND e.empStatus != com.peoplecore.employee.domain.EmpStatus.resign
            """)
    long countHiredThisMonth(
            @Param("companId") UUID companyId,
            @Param("year") int year,
            @Param("month") int month
    );
    @Query("""
        SELECT e.department.id, COUNT(e)
        FROM Employee e
        WHERE e.company.companyId = :companyId
        GROUP BY e.department.id
    """)
    List<Object[]> countByCompanyIdGroupByDeptId(UUID companyId);

    boolean existsByGrade(Grade grade);
}