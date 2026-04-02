package com.peoplecore.hr_service.department.repository;

import com.peoplecore.hr_service.department.domain.Department;
import com.peoplecore.hr_service.department.domain.UseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    List<Department> findByCompanyIdAndIsUseOrderByDeptNameAsc(UUID companyId, UseStatus isUse);

    List<Department> findByCompanyIdAndParentDeptIdAndIsUse(UUID companyId, Long parentDeptId, UseStatus isUse);

    Optional<Department> findByIdAndCompanyId(Long id, UUID companyId);

    boolean existsByCompanyIdAndDeptName(UUID companyId, String deptName);

    boolean existsByCompanyIdAndDeptCode(UUID companyId, String deptCode);

    boolean existsByParentDeptIdAndIsUse(Long parentDeptId, UseStatus isUse);
}
