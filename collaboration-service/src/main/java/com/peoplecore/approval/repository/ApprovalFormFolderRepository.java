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
    List<ApprovalFormFolder> findByFolderCompanyIdAndFolderIsVisibleTrueOrderByFolderSortOrder(UUID companyId);


    // 관리자용 - 전체 폴더 조회 (숨김 포함)
    List<ApprovalFormFolder> findByFolderCompanyIdOrderByFolderSortOrder(UUID companyId);

    // 폴더명 중복 체크
    boolean existsByFolderCompanyIdAndFolderName(UUID companyId, String folderName);

    // 같은 부모 아래 최대 정렬순서 조회
    @Query("SELECT COALESCE(MAX(f.folderSortOrder), 0) FROM ApprovalFormFolder f " +
            "WHERE f.folderCompanyId = :companyId AND " +
            "((:parentId IS NULL AND f.parent IS NULL) OR f.parent.folderId = :parentId)")
    Integer findMaxSortOrder(@Param("companyId") UUID companyId, @Param("parentId") Long parentId);

    // 해당 폴더에 양식이 존재하는지 확인 (삭제 시 검증용)
    @Query("SELECT COUNT(f) > 0 FROM ApprovalForm f WHERE f.folderId.folderId = :folderId")
    boolean existsFormByFolderId(@Param("folderId") Long folderId);

    // 회사별 루트 폴더(부모 없음) 조회 — 회사 초기화 시 멱등성 체크용
    Optional<ApprovalFormFolder> findByFolderCompanyIdAndFolderNameAndParentIsNull(UUID companyId, String folderName);

    // 특정 부모 아래 폴더 조회 — 회사 초기화 시 서브폴더 멱등성 체크용
    Optional<ApprovalFormFolder> findByFolderCompanyIdAndFolderNameAndParent(UUID companyId, String folderName, ApprovalFormFolder parent);
}
