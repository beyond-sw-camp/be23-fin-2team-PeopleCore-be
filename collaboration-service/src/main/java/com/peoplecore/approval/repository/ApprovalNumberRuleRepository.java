package com.peoplecore.approval.repository;

import com.peoplecore.approval.entity.ApprovalNumberRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalNumberRuleRepository extends JpaRepository<ApprovalNumberRule, Long> {
    Optional<ApprovalNumberRule> findByNumberRuleCompanyId(UUID numberRuleCompanyId);


}
