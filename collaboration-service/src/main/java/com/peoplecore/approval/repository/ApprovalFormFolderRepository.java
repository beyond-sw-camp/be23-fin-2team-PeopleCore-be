package com.peoplecore.approval.repository;

import com.peoplecore.approval.entity.ApprovalFormFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalFormFolderRepository extends JpaRepository<ApprovalFormFolder, Long> {

    /* 사원용 폴더 목록 — 미삭제·노출 폴더만 */
    @Query("SELECT f FROM ApprovalFormFolder f " +
            "WHERE f.folderCompanyId = :companyId " +
            "AND f.folderIsVisible = true AND f.isDeleted = false " +
            "ORDER BY f.folderSortOrder")
    List<ApprovalFormFolder> findVisibleByCompanyId(@Param("companyId") UUID companyId);

    /* 관리자용 폴더 목록 — 숨김 포함, 삭제된 것만 제외 */
    @Query("SELECT f FROM ApprovalFormFolder f " +
            "WHERE f.folderCompanyId = :companyId AND f.isDeleted = false " +
            "ORDER BY f.folderSortOrder")
    List<ApprovalFormFolder> findAllByCompanyId(@Param("companyId") UUID companyId);

    /* 폴더명 중복 체크 — 미삭제만 (삭제된 폴더명은 재사용 가능) */
    @Query("SELECT COUNT(f) > 0 FROM ApprovalFormFolder f " +
            "WHERE f.folderCompanyId = :companyId AND f.folderName = :folderName " +
            "AND f.isDeleted = false")
    boolean existsActiveByCompanyIdAndFolderName(@Param("companyId") UUID companyId,
                                                 @Param("folderName") String folderName);

    /* 같은 부모 아래 최대 정렬순서 — 미삭제만 */
    @Query("SELECT COALESCE(MAX(f.folderSortOrder), 0) FROM ApprovalFormFolder f " +
            "WHERE f.folderCompanyId = :companyId AND f.isDeleted = false " +
            "AND ((:parentId IS NULL AND f.parent IS NULL) OR f.parent.folderId = :parentId)")
    Integer findMaxSortOrder(@Param("companyId") UUID companyId, @Param("parentId") Long parentId);

    /* 해당 폴더에 미삭제 양식이 존재하는지 (폴더 삭제 사전 검증) */
    @Query("SELECT COUNT(f) > 0 FROM ApprovalForm f " +
            "WHERE f.folderId.folderId = :folderId AND f.isDeleted = false")
    boolean existsFormByFolderId(@Param("folderId") Long folderId);

    /* 회사 초기화 시 루트 폴더 멱등성 체크 — 미삭제만 */
    @Query("SELECT f FROM ApprovalFormFolder f " +
            "WHERE f.folderCompanyId = :companyId AND f.folderName = :folderName " +
            "AND f.parent IS NULL AND f.isDeleted = false")
    Optional<ApprovalFormFolder> findRootByCompanyIdAndName(@Param("companyId") UUID companyId,
                                                            @Param("folderName") String folderName);

    /* 회사 초기화 시 서브폴더 멱등성 체크 — 미삭제만 */
    @Query("SELECT f FROM ApprovalFormFolder f " +
            "WHERE f.folderCompanyId = :companyId AND f.folderName = :folderName " +
            "AND f.parent = :parent AND f.isDeleted = false")
    Optional<ApprovalFormFolder> findSubFolderByCompanyIdAndNameAndParent(@Param("companyId") UUID companyId,
                                                                          @Param("folderName") String folderName,
                                                                          @Param("parent") ApprovalFormFolder parent);
}
