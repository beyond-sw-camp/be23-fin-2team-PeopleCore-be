package com.peoplecore.approval.repository;

import com.peoplecore.approval.entity.PersonalFolderDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PersonalFolderDocumentRepository extends JpaRepository<PersonalFolderDocument, Long> {

    /** 특정 폴더의 문서 ID 목록 */
    List<PersonalFolderDocument> findByCompanyIdAndEmpIdAndPersonalFolderId(UUID companyId, Long empId, Long personalFolderId);

    /** 사원+문서 매핑 단건 조회 */
    Optional<PersonalFolderDocument> findByCompanyIdAndEmpIdAndDocId(UUID companyId, Long empId, Long docId);

    /**
     * 폴더 내 문서 수 — ApprovalDocument JOIN + notExpired 필터 적용
     * list 쿼리(findPersonalFolderDocument)가 notExpired() 로 만료 문서를 제외하므로
     * count 도 동일 기준으로 맞추어 사이드바 숫자와 리스트 개수 일치
     */
    @Query(value = "SELECT COUNT(*) FROM personal_folder_document fd " +
            "JOIN approval_document d ON d.doc_id = fd.doc_id " +
            "WHERE fd.company_id = :companyId " +
            "AND fd.emp_id = :empId " +
            "AND fd.personal_folder_id = :personalFolderId " +
            "AND (d.retention_year_snapshot IS NULL " +
            "     OR TIMESTAMPADD(YEAR, d.retention_year_snapshot, d.doc_complete_at) > CURRENT_TIMESTAMP)",
            nativeQuery = true)
    int countByCompanyIdAndEmpIdAndPersonalFolderId(@Param("companyId") UUID companyId,
                                                    @Param("empId") Long empId,
                                                    @Param("personalFolderId") Long personalFolderId);

    /** 문서 개별 이동 */
    @Modifying
    @Query("UPDATE PersonalFolderDocument d SET d.personalFolderId = :targetFolderId " +
            "WHERE d.companyId = :companyId AND d.empId = :empId AND d.docId IN :docIds")
    int moveDocuments(@Param("targetFolderId") Long targetFolderId,
                      @Param("companyId") UUID companyId,
                      @Param("empId") Long empId,
                      @Param("docIds") List<Long> docIds);

    /** 폴더 전체 문서 이동 */
    @Modifying
    @Query("UPDATE PersonalFolderDocument d SET d.personalFolderId = :targetFolderId " +
            "WHERE d.companyId = :companyId AND d.empId = :empId AND d.personalFolderId = :folderId")
    int moveAllDocuments(@Param("targetFolderId") Long targetFolderId,
                         @Param("companyId") UUID companyId,
                         @Param("empId") Long empId,
                         @Param("folderId") Long folderId);

    /** 폴더 삭제 시 매핑 존재 여부 */
    boolean existsByCompanyIdAndEmpIdAndPersonalFolderId(UUID companyId, Long empId, Long personalFolderId);
}
