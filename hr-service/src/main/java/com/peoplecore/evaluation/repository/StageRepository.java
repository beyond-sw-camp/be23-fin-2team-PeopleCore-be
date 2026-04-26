package com.peoplecore.evaluation.repository;


import com.peoplecore.evaluation.domain.Stage;
import com.peoplecore.evaluation.domain.StageStatus;
import com.peoplecore.evaluation.domain.StageType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;


// 단계 리포지토리
public interface StageRepository extends JpaRepository<Stage, Long> {

    // 시즌 ID 로 단계 목록 조회
    List<Stage> findBySeason_SeasonId(Long seasonId);

    // 시즌 ID + 단계 타입으로 단건 조회 (GOAL_ENTRY 등 고정 단계 검증용)
    Optional<Stage> findBySeason_SeasonIdAndType(Long seasonId, StageType type);

//  status + startDate <= 오늘 + null 제외 => 진행중 전환 대상
    @Query("""
SELECT st FROM Stage st
WHERE st.status = :status
AND st.startDate IS NOT NULL
AND st.startDate <= :today
""")
    List<Stage> findReadyToStart(@Param("status") StageStatus status,
                                 @Param("today") LocalDate today);

//    status + endDate < 오늘 + null 제외 -> 마감 전환 대상
    @Query("""
SELECT st FROM Stage st
WHERE st.status = :status
AND st.endDate IS NOT NULL
AND st.endDate < :today
""")
    List<Stage> findReadyToFinish(@Param("status") StageStatus status,
                                  @Param("today") LocalDate today);

}
