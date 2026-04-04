package com.peoplecore.approval.repository;

import com.peoplecore.approval.entity.ApprovalStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalStatusHistoryRepository extends JpaRepository<ApprovalStatusHistory, Long> {
    /** 문서의 상태 변경 이력을 시간순 조회 */
    List<ApprovalStatusHistory> findByDocIdOrderByChangedAtAsc(Long docId);
}
