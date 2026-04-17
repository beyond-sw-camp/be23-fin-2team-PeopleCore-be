package com.peoplecore.filevault.repository;

import com.peoplecore.filevault.entity.FileItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FileItemRepository extends JpaRepository<FileItem, Long> {

    List<FileItem> findByFolderIdAndDeletedAtIsNull(Long folderId);

    List<FileItem> findByUploadedByAndDeletedAtIsNull(Long uploadedBy);

    long countByFolderIdAndDeletedAtIsNull(Long folderId);

    List<FileItem> findByFolderIdInAndDeletedAtIsNotNull(List<Long> folderIds);

    List<FileItem> findByFolderIdIn(List<Long> folderIds);
}
