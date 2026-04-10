package com.peoplecore.calendar.repository;


import com.peoplecore.calendar.entity.Events;
import jdk.jfr.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EventsRepository extends JpaRepository<Events, Long>, EventsCustomRepository {


    Page<Events> findByCompanyIdAndIsAllEmployeesTrueAndDeletedAtIsNullOrderByStartAtDesc(UUID companyId, Pageable pageable);
}
