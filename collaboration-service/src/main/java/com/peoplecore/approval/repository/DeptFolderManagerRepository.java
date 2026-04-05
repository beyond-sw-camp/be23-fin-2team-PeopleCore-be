package com.peoplecore.approval.repository;

import com.peoplecore.approval.entity.DeptFolderManager;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeptFolderManagerRepository extends JpaRepository<DeptFolderManager, Long> {

    /** 폴더별 담당자 목록 */
    List<DeptFolderManager> findByDeptAppFolderIdAndCompanyId(Long deptAppFolderId, UUID companyId);

    /** 중복 등록 체크 */
    boolean existsByDeptAppFolderIdAndEmpId(Long deptAppFolderId, Long empId);

    /** 담당자 삭제 */
    @Modifying(clearAutomatically = true)
    void deleteByDeptAppFolderIdAndEmpId(Long deptAppFolderId, Long empId);

    /** 폴더 삭제 시 담당자 일괄 삭제 */
    @Modifying(clearAutomatically = true)
    void deleteByDeptAppFolderId(Long deptAppFolderId);
}
