package com.peoplecore.approval.repository;

import com.peoplecore.approval.entity.ApprovalLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalLineRepository extends JpaRepository<ApprovalLine, Long> {
    List<ApprovalLine> findByDocId_DocIdOrderByLineStep(Long docId);

    void deleteByDocId_DocId(Long docId);

}
