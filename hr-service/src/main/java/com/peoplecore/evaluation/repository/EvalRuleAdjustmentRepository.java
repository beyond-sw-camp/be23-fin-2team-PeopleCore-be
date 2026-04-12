package com.peoplecore.evaluation.repository;

import com.peoplecore.evaluation.domain.EvalRuleAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

// 가감항목 리포지토리
public interface EvalRuleAdjustmentRepository extends JpaRepository<EvalRuleAdjustment, Long> {
}
