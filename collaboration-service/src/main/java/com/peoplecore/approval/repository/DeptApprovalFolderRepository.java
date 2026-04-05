package com.peoplecore.approval.repository;

import com.peoplecore.approval.entity.DeptApprovalFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeptApprovalFolderRepository extends JpaRepository<DeptApprovalFolder, Long> {

    /** 부서 문서함 목록 (정렬 순서) */
    List<DeptApprovalFolder> findByCompanyIdAndDeptIdOrderBySortOrder(UUID companyId, Long deptId);

    /** 단건 조회 (회사 격리) */
    Optional<DeptApprovalFolder> findByDeptAppFolderIdAndCompanyId(Long deptAppFolderId, UUID companyId);

    /** 부서 내 최대 sortOrder 조회 */
    @Query("SELECT COALESCE(MAX(f.sortOrder), 0) FROM DeptApprovalFolder f WHERE f.companyId = :companyId AND f.deptId = :deptId")
    Integer findMaxSortOrder(@Param("companyId") UUID companyId, @Param("deptId") Long deptId);

    /** 이름 중복 체크 */
    boolean existsByCompanyIdAndDeptIdAndFolderName(UUID companyId, Long deptId, String folderName);
}
