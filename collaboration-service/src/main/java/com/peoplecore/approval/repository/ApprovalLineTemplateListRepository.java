package com.peoplecore.approval.repository;

import com.peoplecore.approval.entity.ApprovalLineTemplate;
import com.peoplecore.approval.entity.ApprovalLineTemplateList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalLineTemplateListRepository extends JpaRepository<ApprovalLineTemplateList, Long> {
    List<ApprovalLineTemplateList> findByApprovalLineTemplateId_LineTemIdOrderByLineTemListStep(Long lineTemId);

    void deleteByApprovalLineTemplateId_LineTemId(Long lineTemId);
}
