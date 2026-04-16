package com.peoplecore.attendance.repository;

import com.peoplecore.entity.Holidays;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 출퇴근 판정용 Holidays 조회.
 * NATIONAL: 전역 저장 (companyId IS NULL 무관, 타입만으로 매치). 대체공휴일도 별도 row 로 포함.
 * COMPANY : 해당 회사만.
 */
@Repository
public interface HolidayLookupRepository extends JpaRepository<Holidays, Long> {

    /**
     * 주어진 날짜가 공휴일/사내일정인지 조회.
     * - isRepeating=true  : MONTH/DAY 만 매칭 (매년 반복)
     * - isRepeating=false : date 정확 일치
     * - NATIONAL 전역, COMPANY 는 companyId 일치 필요
     * 반환: 매치 목록. 0~2건 예상. NATIONAL/COMPANY 동시 매치 시 NATIONAL 우선 결정은 호출부.
     */
    @Query("""
            SELECT h FROM Holidays h
             WHERE (
                   (h.holidayType = com.peoplecore.entity.HolidayType.NATIONAL)
                OR (h.holidayType = com.peoplecore.entity.HolidayType.COMPANY
                    AND h.companyId = :companyId)
             )
               AND (
                   (h.isRepeating = true
                    AND FUNCTION('MONTH', h.date) = :month
                    AND FUNCTION('DAY',   h.date) = :day)
                OR (h.isRepeating = false AND h.date = :target)
             )
            """)
    List<Holidays> findMatching(@Param("companyId") UUID companyId,
                                @Param("target") LocalDate target,
                                @Param("month") int month,
                                @Param("day") int day);
}