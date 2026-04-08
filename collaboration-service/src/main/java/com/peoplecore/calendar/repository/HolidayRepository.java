package com.peoplecore.calendar.repository;

import com.peoplecore.calendar.entity.EventsNotifications;
import com.peoplecore.entity.Holidays;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface HolidayRepository extends JpaRepository<Holidays, Long> {

    @Query("SELECT h FROM Holiday h WHERE h.companyId = :companyId AND ((h.isRepeating = true) OR (h.date BETWEEN :start AND :end))")
    List<Holidays> findByCompanyIdAndPeriod(@Param("companyId")UUID companyId, @Param("start")LocalDate start, @Param("end") LocalDate end);

}
