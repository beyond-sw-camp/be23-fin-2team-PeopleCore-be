package com.peoplecore.evaluation.repository;

import com.peoplecore.evaluation.domain.EvalGrade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// 등급 리포지토리 (테이블명 grade, 클래스 EvalGrade)
public interface EvalGradeRepository extends JpaRepository<EvalGrade, Long>, EvalGradeRepositoryCustom {
    List<EvalGrade> findBySeason_SeasonId(Long seasonId);

}
