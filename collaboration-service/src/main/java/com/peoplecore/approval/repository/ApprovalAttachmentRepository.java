package com.peoplecore.approval.repository;

import com.peoplecore.approval.entity.ApprovalAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalAttachmentRepository extends JpaRepository<ApprovalAttachment, Long> {

    /** 문서의 첨부파일 목록 조회 */
    List<ApprovalAttachment> findByDocId_DocId(Long docId);

    // ApprovalAttachmentRepository에 추가
    @Query("SELECT a FROM ApprovalAttachment a JOIN FETCH a.docId WHERE a.attachId = :attachId")
    Optional<ApprovalAttachment> findWithDocById(@Param("attachId") Long attachId);

    /** 문서의 첨부파일 전체 삭제 */
    void deleteByDocId_DocId(Long docId);
}
