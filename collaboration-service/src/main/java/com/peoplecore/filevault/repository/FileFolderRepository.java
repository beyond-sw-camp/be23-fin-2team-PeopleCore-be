package com.peoplecore.filevault.repository;

import com.peoplecore.filevault.entity.FileFolder;
import com.peoplecore.filevault.entity.FolderType;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileFolderRepository extends JpaRepository<FileFolder, Long> {

    List<FileFolder> findByCompanyIdAndTypeAndParentFolderIdIsNullAndDeletedAtIsNull(
        UUID companyId, FolderType type
    );

    List<FileFolder> findByParentFolderIdAndDeletedAtIsNull(Long parentFolderId);

    Optional<FileFolder> findByDeptIdAndIsSystemDefaultTrueAndDeletedAtIsNull(Long deptId);

    Optional<FileFolder> findByOwnerEmpIdAndTypeAndDeletedAtIsNull(Long ownerEmpId, FolderType type);

    List<FileFolder> findByCompanyIdAndTypeAndDeletedAtIsNull(UUID companyId, FolderType type);

    boolean existsByParentFolderIdAndNameAndDeletedAtIsNull(Long parentFolderId, String name);

    List<FileFolder> findByCompanyId(UUID companyId);

    List<FileFolder> findByParentFolderIdIn(List<Long> parentFolderIds);

    @Query("SELECT DISTINCT f.companyId FROM FileFolder f")
    List<UUID> findDistinctCompanyIds();
}
