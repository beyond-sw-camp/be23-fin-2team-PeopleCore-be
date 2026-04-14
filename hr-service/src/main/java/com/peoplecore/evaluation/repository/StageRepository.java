package com.peoplecore.evaluation.repository;


import com.peoplecore.evaluation.domain.Stage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


// 단계 리포지토리
public interface StageRepository extends JpaRepository<Stage, Long> {

    // 시즌 ID 로 단계 목록 조회
    List<Stage> findBySeason_SeasonId(Long seasonId);
}
