package com.peoplecore.filevault.permission.repository;

import com.peoplecore.filevault.permission.entity.FileBoxAdminConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FileBoxAdminConfigRepository extends JpaRepository<FileBoxAdminConfig, Long> {
    Optional<FileBoxAdminConfig> findByCompanyId(UUID companyId);
}
