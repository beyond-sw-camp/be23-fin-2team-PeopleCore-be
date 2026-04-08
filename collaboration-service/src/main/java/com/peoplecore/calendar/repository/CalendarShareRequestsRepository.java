package com.peoplecore.calendar.repository;

import com.peoplecore.calendar.entity.CalendarShareRequests;
import com.peoplecore.calendar.enums.ShareStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CalendarShareRequestsRepository extends JpaRepository<CalendarShareRequests, Long> {

//    내가 요청보낸 관심캘린더 목록
    Page<CalendarShareRequests> findByCompanyIdAndFromEmlIdOrderByRequestedAtDesc(UUID companyId, Long fromEmpId, Pageable pageable);

//    나에게 요청온 관심캘린더 목록
    Page<CalendarShareRequests> findByCompanyIdAndFromEmpIdOrderByRequestedAtDesc(UUID companyId, Long toEmpId, Pageable pageable);

//    중복 요청 방지
     boolean existsByCompanyIdAndFromEmpIdAndToEmpIdAndShareStatus(UUID companyId, Long fromEmpId, Long toEmpId, ShareStatus status);

}
