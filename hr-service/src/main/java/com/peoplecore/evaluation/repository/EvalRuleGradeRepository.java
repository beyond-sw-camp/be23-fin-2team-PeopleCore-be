package com.peoplecore.evaluation.repository;

import com.peoplecore.evaluation.domain.EvalRuleGrade;
import org.springframework.data.jpa.repository.JpaRepository;

// 등급정의 리포지토리
public interface EvalRuleGradeRepository extends JpaRepository<EvalRuleGrade, Long> {
}
