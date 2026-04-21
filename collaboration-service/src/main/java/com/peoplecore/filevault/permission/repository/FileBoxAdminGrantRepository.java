package com.peoplecore.filevault.permission.repository;

import com.peoplecore.filevault.permission.entity.FileBoxAdminGrant;
import com.peoplecore.filevault.permission.entity.FileBoxAdminMode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FileBoxAdminGrantRepository extends JpaRepository<FileBoxAdminGrant, Long> {
    List<FileBoxAdminGrant> findByCompanyIdAndMode(UUID companyId, FileBoxAdminMode mode);

    boolean existsByCompanyIdAndModeAndTargetIdIn(UUID companyId, FileBoxAdminMode mode, List<Long> targetIds);

    void deleteByCompanyId(UUID companyId);
}
