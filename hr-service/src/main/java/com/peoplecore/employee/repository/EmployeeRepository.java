package com.peoplecore.employee.repository;

import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.grade.domain.Grade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
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
    int countByCompany_CompanyIdAndEmpStatusNot(UUID companyId, EmpStatus status);

    int countByCompany_CompanyIdAndEmpStatus(UUID companyId, EmpStatus status);

    int countByCompany_CompanyIdAndDept_DeptId(UUID companyId, Long deptId);

    @Query("""
            SELECT COUNT(e) FROM Employee e
            WHERE e.company.companyId = :companyId
            AND YEAR(e.empHireDate) = :year
            AND MONTH(e.empHireDate) = :month
            AND e.empStatus != com.peoplecore.employee.domain.EmpStatus.RESIGNED
            """)
    int countHiredThisMonth(
            @Param("companyId") UUID companyId,
            @Param("year") int year,
            @Param("month") int month
    );


    //    카드조회 (인사 현황)
//    해당달의 퇴직자
    @Query("""
            SELECT COUNT(e) FROM Employee e
            WHERE e.company.companyId = :companyId
            AND YEAR(e.empResign) = :year
            AND MONTH(e.empResign) = :month""")
    int countResignedThisMonth(@Param("companyId") UUID companyId, @Param("year") int year, @Param("month") int month);

    //    계약만료 30일 이내 예정자
    @Query("""
            SELECT e FROM Employee e
            JOIN FETCH e.dept
            WHERE e.company.companyId = :companyId
            AND e.contractEndDate IS NOT NULL
            AND e.contractEndDate BETWEEN :now AND :deadline
            AND e.empStatus = com.peoplecore.employee.domain.EmpStatus.ACTIVE
            """)
    List<Employee> findExpiringContracts(@Param("companyId") UUID companyId, @Param("now") LocalDate now, @Param("deadline") LocalDate deadline);


    //    사번 채번
//    동일 사번 여부 체크
    boolean existsByCompany_CompanyIdAndEmpNum(UUID companyId, String empNum);

    //    특정 prefix로 시작하는 사번 개수 조회
    @Query("""
            SELECT COUNT(e) FROM Employee e
            WHERE e.company.companyId = :companyId
            AND e.empNum LIKE :prefix%
            """)
    long countByCompanyIdAndEmpNumStartingWith(@Param("companyId") UUID companyId, @Param("prefix") String prefix);

    @Query("""
                SELECT e FROM Employee e
                JOIN FETCH e.title t
                JOIN FETCH e.grade g
                WHERE e.dept.deptId = :deptId
                AND e.company.companyId = :companyId
                AND e.empStatus != com.peoplecore.employee.domain.EmpStatus.RESIGNED
            """)
    List<Employee> findTitleHoldersByDeptId(
            @Param("companyId") UUID companyId,
            @Param("deptId") Long deptId
    );


    //사원상세조회
    Optional<Employee> findByEmpIdAndCompany_CompanyId(Long empId, UUID companyId);


    //    재직자 조회
    @Query("""
            SELECT e FROM Employee e
            JOIN FETCH e.dept
            JOIN FETCH e.grade
            WHERE e.company.companyId = :companyId
            AND e.empStatus != com.peoplecore.employee.domain.EmpStatus.RESIGNED
            """)
    List<Employee> findActiveEmployeesWithDeptAndGrade(@Param("companyId") UUID companyId);

    @Query("""
            
                        SELECT e FROM Employee e
            JOIN FETCH e.dept
            JOIN FETCH e.grade
            WHERE e.company.companyId = :companyId
            AND e.dept.deptId = :deptId
            AND e.empStatus != com.peoplecore.employee.domain.EmpStatus.RESIGNED
            """)
    List<Employee> findActiveByCompanyAndDept(@Param("companyId") UUID companyId, @Param("deptId") Long deptId);


    //    최근 6개월 입사자 조회
    @Query("""
            SELECT e FROM Employee e
            WHERE e.company.companyId = :companyId
            AND e.empHireDate >= :fromDate
            """)
    List<Employee> findHiredAfter(@Param("companyId") UUID companyId, @Param("fromDate") LocalDate fromDate);

    //    최근 6개월 퇴사자 조회
    @Query("""
            SELECT e FROM Employee e
            WHERE e.company.companyId = :companyId
            AND e.empResign IS NOT NULL
            AND e.empResign >= :fromDate
            """)
    List<Employee> findResignedAfter(@Param("companyId") UUID companyId, @Param("fromDate") LocalDate fromDate);


    // 산재보험 업종 삭제시, 사원에 배정되어있는지 체크
    boolean existsByJobTypes_JobTypesId(Long jobTypesId);
}
