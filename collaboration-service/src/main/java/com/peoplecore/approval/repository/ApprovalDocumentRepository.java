package com.peoplecore.approval.repository;

import com.peoplecore.approval.entity.ApprovalDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
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

    /*문서함 내 문서 카운트 */
    int countByPersonalFolderIdAndCompanyId(Long personalFolderId, UUID companyId);

    /**
     * 문서 이동: personalFolderId 일괄 변경
     */
    @Modifying
    @Query("UPDATE ApprovalDocument d SET d.personalFolderId = :targetFolderId WHERE d.docId IN :docIds AND d.companyId = :companyId")
    int updatePersonalFolderIdByDocIds(@Param("targetFolderId") Long targetFolderId,
                                       @Param("docIds") List<Long> docIds,
                                       @Param("companyId") UUID companyId);

    /**
     * 폴더 내 전체 문서 이동
     */
    @Modifying
    @Query("UPDATE ApprovalDocument d SET d.personalFolderId = :targetFolderId WHERE d.personalFolderId = :folderId AND d.companyId = :companyId")
    int updatePersonalFolderIdByFolderId(@Param("targetFolderId") Long targetFolderId,
                                         @Param("folderId") Long folderId,
                                         @Param("companyId") UUID companyId);

}
