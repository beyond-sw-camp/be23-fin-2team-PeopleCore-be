package com.peoplecore.permission.repository;

import com.peoplecore.permission.domain.Permission;
import com.peoplecore.permission.domain.PermissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long>,PermissionRepositoryCustom {

    // 사원별 특정 상태(GRANTED/REVOKED)의 최신 이력 1건씩 조회
    @Query("""
           SELECT p FROM Permission p
           WHERE p.employee.empId IN :empIds AND p.status = :status
           AND p.processedAt = (
               SELECT MAX(p2.processedAt) FROM Permission p2
               WHERE p2.employee.empId = p.employee.empId AND p2.status = :status
           )
           """)
    List<Permission> findLatestByEmpIdsAndStatus(@Param("empIds") List<Long> empIds, @Param("status") PermissionStatus status);

    // 회사 전체 권한 변경 이력 조회 (최신순)
    @Query("""
           SELECT p FROM Permission p
           JOIN FETCH p.employee
           LEFT JOIN FETCH p.grantor
           WHERE p.employee.company.companyId = :companyId
           ORDER BY p.processedAt DESC
           """)
    List<Permission> findAllByCompanyOrderByProcessedAtDesc(@Param("companyId") UUID companyId);
}
