package com.peoplecore.evaluation.repository;

import com.peoplecore.evaluation.domain.EvalRuleItem;
import org.springframework.data.jpa.repository.JpaRepository;

// 평가항목 리포지토리
public interface EvalRuleItemRepository extends JpaRepository<EvalRuleItem, Long> {
}
