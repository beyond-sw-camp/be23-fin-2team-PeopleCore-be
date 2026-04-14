package com.peoplecore.evaluation.repository;

import com.peoplecore.evaluation.domain.EvalSeasonStatus;
import com.peoplecore.evaluation.domain.Season;
import com.peoplecore.evaluation.dto.SeasonDropDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// 평가시즌 리포지토리
public interface SeasonRepository extends JpaRepository<Season, Long> {

    @Query("""
            SELECT s 
            FROM Season s
            WHERE s.company.companyId = :companyId
            ORDER BY s.startDate DESC, s.seasonId DESC
            """)
    List<Season> findAllByCompany(@Param("companyId") UUID companyId);

    // 활성 시즌 드롭다운 목록 (CLOSED 제외, id/name만)
    @Query("""
            SELECT new com.peoplecore.evaluation.dto.SeasonDropDto(s.seasonId, s.name)
            FROM Season s
            WHERE s.company.companyId = :companyId
            AND s.status != com.peoplecore.evaluation.domain.EvalSeasonStatus.CLOSED
            ORDER BY s.startDate DESC, s.seasonId DESC
            """)
    List<SeasonDropDto> findActiveByCompany(@Param("companyId") UUID companyId);

//    상태+시작일 <= 오늘 -> open대상
    List<Season>findByStatusAndStartDateLessThanEqual(EvalSeasonStatus status, LocalDate today);

//    상태 + 종료일 < 오늘-> close대상
    List<Season>findByStatusAndEndDateBefore(EvalSeasonStatus status, LocalDate today);
}
