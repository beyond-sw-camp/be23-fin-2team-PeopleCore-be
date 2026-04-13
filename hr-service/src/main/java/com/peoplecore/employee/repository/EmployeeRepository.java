package com.peoplecore.employee.repository;

import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.grade.domain.Grade;
import com.peoplecore.title.domain.Title;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    boolean existsByTitle(Title title);




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
AND YEAR(e.empResignDate) = :year
AND MONTH(e.empResignDate) = :month""")
    int countResignedThisMonth(@Param("companyId")UUID companyId, @Param("year")int year,@Param("month")int month);

//    계약만료 30일 이내 예정자
    @Query("""
SELECT e FROM Employee e
JOIN FETCH e.dept
WHERE e.company.companyId = :companyId
AND e.contractEndDate IS NOT NULL
AND e.contractEndDate BETWEEN :now AND :deadline
AND e.empStatus = com.peoplecore.employee.domain.EmpStatus.ACTIVE
""")
    List<Employee>findExpiringContracts(@Param("companyId")UUID companyId, @Param("now")LocalDate now, @Param("deadline")LocalDate deadline);


    //    사번 채번
//    계약 만료 예정자 건수만 조회
    @Query("""
SELECT COUNT(e) FROM Employee e
WHERE e.company.companyId = :companyId
AND e.contractEndDate BETWEEN : now AND : deadline
AND e.empStatus = com.peoplecore.employee.domain.EmpStatus.ACTIVE
""")
    int countExpiringContracts(@Param("companyId")UUID companyId,
                               @Param("now")LocalDate now,
                               @Param("deadline")LocalDate deadline);


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

//    비관적 락: 해당prefix 중 가장 큰 값 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT MAX(e.empNum)FROM Employee e " +
            "WHERE e.company.companyId = :companyId " +
            "AND e.empNum LIKE :prefix%")
    Optional<String>findMaxEmpNumWithLock(@Param("companyId") UUID companyId,
                                          @Param("prefix") String prefix);


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
    List<Employee>findActiveEmployeesWithDeptAndGrade(@Param("companyId")UUID companyId);

    @Query("""

            SELECT e FROM Employee e
JOIN FETCH e.dept
JOIN FETCH e.grade
WHERE e.company.companyId = :companyId
AND e.dept.deptId = :deptId
AND e.empStatus != com.peoplecore.employee.domain.EmpStatus.RESIGNED
""")
    List<Employee>findActiveByCompanyAndDept(@Param("companyId")UUID companyId,@Param("deptId") Long deptId);


//    최근 6개월 입사자 조회
    @Query("""
SELECT e FROM Employee e
WHERE e.company.companyId = :companyId
AND e.empHireDate >= :fromDate
""")
    List<Employee>findHiredAfter(@Param("companyId")UUID companyId, @Param("fromDate")LocalDate fromDate);

    //    최근 6개월 퇴사자 조회
    @Query("""
SELECT e FROM Employee e
WHERE e.company.companyId = :companyId
AND e.empResignDate IS NOT NULL
AND e.empResignDate >= :fromDate
""")
    List<Employee>findResignedAfter(@Param("companyId")UUID companyId,@Param("fromDate")LocalDate fromDate);


    // 산재보험 업종 삭제시, 사원에 배정되어있는지 체크
    boolean existsByJobTypes_JobTypesId(Long jobTypesId);


//    캘린더 목록 조회시 여러 사원 한번에 조회(dept,grade,title LAZY조회로 N+1 발행하므로 query문으로 해결
    @Query("SELECT e FROM Employee e JOIN FETCH e.dept JOIN FETCH e.grade LEFT JOIN FETCH e.title WHERE e.empId IN :empIds AND e.deleteAt IS NULL")
    List<Employee> findByEmpIdsWithDeptAndGrade(@Param("empIds") List<Long> empIds);


    /* 근무 그룹별 소속 사원 수 조회*/
    Long countByWorkGroup_WorkGroupId(Long workGroupId);

    /* 근무 그룹 별 소속 사원 (페이지네이션)*/
    Page<Employee> findByWorkGroup_WorkGroupId(Long workGroupId, Pageable pageable);

/* 근무 그룹 전체 소속 사원 조회*/
    @Query("""
           SELECT e FROM Employee e
           LEFT JOIN FETCH e.dept
           LEFT JOIN FETCH e.grade
           LEFT JOIN FETCH e.title
           WHERE e.workGroup.workGroupId = :workGroupId
           """)
    List<Employee> findAllByWorkGroupIdFetchJoin(@Param("workGroupId") Long workGroupId);

    /* 특정 근무 그룹 소속 사원중 지정된 ID 목록에 해당하는 사원 조회*/
    @Query("""
           SELECT e FROM Employee e
           LEFT JOIN FETCH e.dept
           LEFT JOIN FETCH e.grade
           LEFT JOIN FETCH e.title
           WHERE e.workGroup.workGroupId = :workGroupId
             AND e.empId IN :empIds
           """)
    List<Employee> findByWorkGroupIdAndEmpIdsFetchJoin(@Param("workGroupId") Long workGroupId,
                                                       @Param("empIds") List<Long> empIds);
}
