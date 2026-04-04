package com.peoplecore.department.repository;

import com.peoplecore.department.domain.Department;
import com.peoplecore.department.domain.UseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    List<Department> findByCompany_CompanyIdAndIsUseOrderByDeptNameAsc(UUID companyId, UseStatus isUse);

    List<Department> findByCompany_CompanyIdAndParentDeptIdAndIsUse(UUID companyId, Long parentDeptId, UseStatus isUse);

    Optional<Department> findByDeptIdAndCompany_CompanyId(Long deptId, UUID companyId);

    boolean existsByCompany_CompanyIdAndDeptNameAndIsUse(UUID companyId, String deptName, UseStatus isUse);

    boolean existsByCompany_CompanyIdAndDeptCodeAndIsUse(UUID companyId, String deptCode, UseStatus isUse);


    boolean existsByParentDeptIdAndIsUse(Long parentDeptId, UseStatus isUse);

    Optional<Department> findByDeptIdAndIsUse(Long deptId, UseStatus isUse);

    Optional<Department> findByCompany_CompanyIdAndDeptName(UUID companyId, String deptName);
}
