package com.peoplecore.evaluation.repository;

import com.peoplecore.evaluation.domain.EvalGrade;
import com.peoplecore.evaluation.dto.AutoGradeCountDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

// 등급 리포지토리 (테이블명 grade, 클래스 EvalGrade)
public interface EvalGradeRepository extends JpaRepository<EvalGrade, Long>, EvalGradeRepositoryCustom {

//    시즌 전체 EvalGrade 조회 (산정/보정 로직에서 순회용)
    List<EvalGrade> findBySeason_SeasonId(Long seasonId);


//    시즌의 autoGrade 별 인원수 집계 (null 은 미산정 -> 제외)
//    - 6번 분포 계산용
    @Query("""
           SELECT new com.peoplecore.evaluation.dto.AutoGradeCountDto(g.autoGrade, COUNT(g))
           FROM EvalGrade g
           WHERE g.season.seasonId = :seasonId
             AND g.autoGrade IS NOT NULL
           GROUP BY g.autoGrade
           """)
    List<AutoGradeCountDto> countByAutoGradeGroup(@Param("seasonId") Long seasonId);


//    시즌의 보정된 사원 수 (isCalibrated=true)
//    - 6번 "현재 보정 건수 N건" 표시용
    long countBySeason_SeasonIdAndIsCalibratedTrue(Long seasonId);
}
