package com.peoplecore.approval.repository;

import com.peoplecore.approval.entity.PersonalApprovalFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PersonalApprovalFolderRepository extends JpaRepository<PersonalApprovalFolder, Long> {
    /*개인 문서함 조회 */
    List<PersonalApprovalFolder> findByCompanyIdAndEmpIdOrderBySortOrder(UUID companyId, Long empId);

    /*단건 조회 */
    Optional<PersonalApprovalFolder> findByPersonalFolderIdAndCompanyId(Long personalFolderId, UUID companyId);

    /* 단건 조회 (사원 격리) */
    Optional<PersonalApprovalFolder> findByPersonalFolderIdAndCompanyIdAndEmpId(Long personalFolderId, UUID companyId, Long empId);

    /*사원내 최대 sortOrder 조회 */
    @Query("SELECT COALESCE(MAX(f.sortOrder), 0) FROM PersonalApprovalFolder f WHERE f.companyId = :companyId AND f.empId = :empId")
    Integer findMaxSortOrder(@Param("companyId") UUID companyId, @Param("empId") Long empId);

    /*이름 중복체크 */
    boolean existsByCompanyIdAndEmpIdAndFolderName(UUID companyId, Long empId, String folderName);


}
