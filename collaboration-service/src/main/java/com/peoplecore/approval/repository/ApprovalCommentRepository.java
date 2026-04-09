package com.peoplecore.approval.repository;

import com.peoplecore.common.entity.CommonComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ApprovalCommentRepository extends JpaRepository<CommonComment, Long> {

    /** 문서의 댓글 목록 (생성순) */
    List<CommonComment> findByCompanyIdAndEntityTypeAndEntityIdOrderByCreatedAtAsc(
            UUID companyId, String entityType, Long entityId);
}
