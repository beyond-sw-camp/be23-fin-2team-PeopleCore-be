package com.peoplecore.approval.repository;

import com.peoplecore.approval.entity.ApprovalFormFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ApprovalFormFolderRepository extends JpaRepository<ApprovalFormFolder, Long> {
    List<ApprovalFormFolder> findByFolderCompanyIdAndFolderIsVisibleTrueOrderByFolderSortOrder(UUID companyId);
}
