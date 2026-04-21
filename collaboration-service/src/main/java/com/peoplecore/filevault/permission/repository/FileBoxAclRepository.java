package com.peoplecore.filevault.permission.repository;

import com.peoplecore.filevault.permission.entity.FileBoxAcl;
import com.peoplecore.filevault.permission.entity.FileBoxAclId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FileBoxAclRepository extends JpaRepository<FileBoxAcl, FileBoxAclId> {
    List<FileBoxAcl> findByFolderId(Long folderId);

    Optional<FileBoxAcl> findByFolderIdAndEmpId(Long folderId, Long empId);

    boolean existsByFolderIdAndEmpId(Long folderId, Long empId);

    void deleteByFolderIdAndEmpId(Long folderId, Long empId);

    void deleteByFolderId(Long folderId);

    List<FileBoxAcl> findByEmpId(Long empId);
}
