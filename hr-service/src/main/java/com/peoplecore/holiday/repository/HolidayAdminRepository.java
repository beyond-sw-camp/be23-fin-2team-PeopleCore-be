package com.peoplecore.holiday.repository;

import com.peoplecore.entity.HolidayType;
import com.peoplecore.entity.Holidays;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

//사내 휴일 관리 전용
public interface HolidayAdminRepository extends JpaRepository<Holidays, Long> {

    //한 회사가 볼 수 있는 모든 휴일
    // 연도별 조회 (NATIONAL 전체 + COMPANY 해당 회사 분만)
    @Query("""
    SELECT h FROM Holidays h
     WHERE h.holidayType = HolidayType.NATIONAL
        OR (h.holidayType = HolidayType.COMPANY
            AND h.companyId = :companyId)
    """)
    List<Holidays> findAllForCompany(@Param("companyId") UUID companyId);

    // 비반복 휴일 중복 검증 (회사 + 날짜 + 비반복 일치)
    Optional<Holidays> findFirstByCompanyIdAndHolidayTypeAndDateAndIsRepeating(
        UUID companyId, HolidayType holidayType, LocalDate date, Boolean isRepeating);

    // 반복 휴일 중복 검증용 (월/일 매칭)
    @Query("""
    SELECT h FROM Holidays h
     WHERE h.companyId = :companyId
       AND h.holidayType = HolidayType.COMPANY
       AND h.isRepeating = true
       AND FUNCTION('MONTH', h.date) = :month
       AND FUNCTION('DAY',   h.date) = :day
    """)
    Optional<Holidays> findRepeatingCompanyByMonthDay(
            @Param("companyId") UUID companyId,
            @Param("month") int month,
            @Param("day") int day);

}
