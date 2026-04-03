package com.peoplecore.approval.repository;

import com.peoplecore.approval.entity.ApprovalAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalAttachmentRepository extends JpaRepository<ApprovalAttachment, Long> {

    /** 문서의 첨부파일 목록 조회 */
    List<ApprovalAttachment> findByDocId_DocId(Long docId);

    /** 문서의 첨부파일 전체 삭제 */
    void deleteByDocId_DocId(Long docId);
}
