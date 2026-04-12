package com.peoplecore.evaluation.repository;

import com.peoplecore.evaluation.domain.Stage;
import org.springframework.data.jpa.repository.JpaRepository;

// 단계 리포지토리
public interface StageRepository extends JpaRepository<Stage, Long> {
}
