package com.peoplecore.approval.repository;

import com.peoplecore.approval.entity.ApprovalDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalDocumentRepository extends JpaRepository<ApprovalDocument, Long>, ApprovalDocumentCustomRepository {
    Optional<ApprovalDocument> findByDocIdAndCompanyId(Long docId, UUID companyId);

    /**
     * 문서 + 양식 JOIN FETCH (상세 조회용)
     */
    @Query("SELECT d FROM ApprovalDocument d " +
            "JOIN FETCH d.formId f " +
            "WHERE d.companyId = :companyId AND d.docId = :docId")
    Optional<ApprovalDocument> findWithFormById(
            @Param("companyId") UUID companyId, @Param("docId") Long docId);


}
