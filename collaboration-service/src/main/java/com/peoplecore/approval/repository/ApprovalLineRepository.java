package com.peoplecore.approval.repository;

import com.peoplecore.approval.entity.ApprovalLine;
import com.peoplecore.approval.entity.ApprovalRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalLineRepository extends JpaRepository<ApprovalLine, Long> {
    List<ApprovalLine> findByDocId_DocIdOrderByLineStep(Long docId);

    void deleteByDocId_DocId(Long docId);

    /* 특정 문서에서 특정 사원의 결재선 조회 */
    Optional<ApprovalLine> findByDocId_DocIdAndEmpId(Long docId, Long empId);

    /* 특정 문서의 결재자(Approval)만 조회 (결재 순대로) */
    @Query("SELECT al FROM ApprovalLine al JOIN FETCH al.docId " +
            "WHERE al.docId.docId = :docId AND al.approvalRole = :approvalRole " +
            "ORDER BY al.lineStep")
    List<ApprovalLine> findByDocId_DocIdOrderByLineStep(@Param("docId") Long docId, @Param("approvalRole") ApprovalRole approvalRole);
}
