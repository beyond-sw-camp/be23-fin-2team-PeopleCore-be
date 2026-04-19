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


//    시즌의 finalGrade 별 인원수 집계 (보정 반영된 현재 분포, null=미배분 제외)
//    - 6번 분포 계산용 (+9번 시뮬레이션 재사용)
//    - autoGrade 아닌 finalGrade 로 집계 = 보정으로 등급 이동한 상태를 반영
    @Query("""
           SELECT new com.peoplecore.evaluation.dto.AutoGradeCountDto(g.finalGrade, COUNT(g))
           FROM EvalGrade g
           WHERE g.season.seasonId = :seasonId
             AND g.finalGrade IS NOT NULL
           GROUP BY g.finalGrade
           """)
    List<AutoGradeCountDto> countByAutoGradeGroup(@Param("seasonId") Long seasonId);


//    시즌의 보정된 사원 수 (isCalibrated=true)
//    - 6번 "현재 보정 건수 N건" 표시용
    long countBySeason_SeasonIdAndIsCalibratedTrue(Long seasonId);


//    5번 강제배분 - 현재 랭킹 대상 인원 (biasAdjustedScore 있는 사람)
    long countBySeason_SeasonIdAndBiasAdjustedScoreNotNull(Long seasonId);


//    5번 강제배분 - 이전 배분 인원 (autoGrade 있는 사람)
//    - 위 두 수가 다르면 cohort 변화 -> 재산정 필요
    long countBySeason_SeasonIdAndAutoGradeNotNull(Long seasonId);
}
