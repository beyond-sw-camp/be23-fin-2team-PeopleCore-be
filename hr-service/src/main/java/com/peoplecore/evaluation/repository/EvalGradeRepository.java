package com.peoplecore.evaluation.repository;

import com.peoplecore.evaluation.domain.EvalGrade;
import com.peoplecore.evaluation.dto.AutoGradeCountDto;
import com.peoplecore.evaluation.dto.TeamBiasResponseDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    //    10번 - 시즌 전체 대상 인원
    long countBySeason_SeasonId(Long seasonId);

    //    10번 - 배정 완료 인원 (finalGrade IS NOT NULL)
    long countBySeason_SeasonIdAndFinalGradeNotNull(Long seasonId);

    //    5번 강제배분 - 현재 랭킹 대상 인원 (biasAdjustedScore 있는 사람)
    long countBySeason_SeasonIdAndBiasAdjustedScoreNotNull(Long seasonId);

    //    5번 강제배분 - 이전 배분 인원 (autoGrade 있는 사람)
//    - 위 두 수가 다르면 cohort 변화 -> 재산정 필요
    long countBySeason_SeasonIdAndAutoGradeNotNull(Long seasonId);

    //    팀장 편향 보정(Z-score) 팀별 효과 집계 - 자동 산정 화면 차트용
//    - 부서별로 managerScore(보정 전) / managerScoreAdjusted(보정 후) 평균과 인원 수
//    - managerScore NULL 인 사원(평가 미제출)은 제외
    @Query("""
            SELECT new com.peoplecore.evaluation.dto.TeamBiasResponseDto.Team(
                 d.deptId,
                 d.deptName,
                 COUNT(g),
                 AVG(g.managerScore),
                 AVG(g.managerScoreAdjusted)
            )
            FROM EvalGrade g
            JOIN g.emp e
            JOIN e.dept d
            WHERE g.season.seasonId = :seasonId
              AND g.managerScore IS NOT NULL
            GROUP BY d.deptId, d.deptName
            ORDER BY d.deptId
            """)
    List<TeamBiasResponseDto.Team> findTeamBiasSummary(@Param("seasonId") Long seasonId);

    //    12번 시즌 내 미산정자 empId목록(서버 내 ack재검증)
    @Query("""
            SELECT g.emp.empId 
            FROM EvalGrade g
            WHERE g.season.seasonId = :seasonId
            AND g.finalGrade IS NULL
            """)
    List<Long> findUnassignedEmpIds(@Param("seasonId") Long seasonId);

//    12번 배정된 EvalGrade전체에 lockedAt세팅
    @Modifying
    @Query("""
            UPDATE EvalGrade g
            SET g.lockedAt = :now
            WHERE g.season.seasonId = :seasonId
            AND g.finalGrade IS NOT NULL
            """)
    int lockAllAssigned(@Param("seasonId") Long seasonId, @Param("now") LocalDateTime now);


}
