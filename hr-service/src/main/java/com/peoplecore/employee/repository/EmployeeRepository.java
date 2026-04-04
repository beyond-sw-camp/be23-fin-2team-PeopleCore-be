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

    Optional<Employee> findByCompany_CompanyIdAndEmpEmail(UUID companyId, String empEmail);

    Optional<Employee> findByEmpNum(String empNum);

    boolean existsByCompany_CompanyIdAndEmpEmail(UUID companyId, String empEmail);

    boolean existsByEmpNum(String empNum);

    Optional<Employee> findByCompany_CompanyIdAndEmpNameAndEmpPhone(UUID companyId, String empName, String empPhone);

    Optional<Employee> findByEmpPhone(String empPhone);


    @Query("""
        SELECT e.dept.deptId, COUNT(e)
        FROM Employee e
        WHERE e.company.companyId = :companyId
        GROUP BY e.dept.deptId
    """)
    List<Object[]> countByCompanyIdGroupByDeptId(UUID companyId);

    boolean existsByGrade(Grade grade);








    /// ////////rim 사원관리

    //  카드조회용
    long countByCompany_CompanyIdAndEmpStatusNot(UUID companyId, EmpStatus status);

    long countByCompany_CompanyIdAndEmpStatus(UUID companyId, EmpStatus status);
    long countByCompany_CompanyIdAndDept_DeptId(UUID companyId, Long deptId);

    @Query("""
            SELECT COUNT(e) FROM Employee e
            WHERE e.company.companyId = :companyId
            AND YEAR(e.empHireDate) = :year
            AND MONTH(e.empHireDate) = :month
            AND e.empStatus != com.peoplecore.employee.domain.EmpStatus.RESIGNED
            """)
    long countHiredThisMonth(
            @Param("companyId") UUID companyId,
            @Param("year") int year,
            @Param("month") int month
    );

//    사번 채번
//    동일 사번 여부 체크
    boolean existsByCompany_CompanyIdAndEmpNum(UUID companyId, String empNum);

//    특정 prefix로 시작하는 사번 개수 조회
    @Query("""
SELECT COUNT(e) FROM Employee e
WHERE e.company.companyId = :companyId
AND e.empNum LIKE :prefix%
""")
    long countByCompanyIdAndEmpNumStartingWith(@Param("companyId")UUID companyId, @Param("prefix")String prefix);


}
