package com.peoplecore.alarm.repository;

import com.peoplecore.entity.CommonAlarm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CommonAlarmRepository extends JpaRepository<CommonAlarm, Long> {
    /*전체 알림 조회 */
    Page<CommonAlarm> findByCompanyIdAndAlarmEmpIdOrderByCreatedAtDesc(UUID companyId, Long empId, Pageable pageable);

    /*안 읽은 알림 조회*/
    Page<CommonAlarm> findByCompanyIdAndAlarmEmpIdAndAlarmIsReadFalseOrderByCreatedAtDesc(
            UUID companyId, Long empId, Pageable pageable);

    /* 안읽은 알림 개수 */
    long countByCompanyIdAndAlarmEmpIdAndAlarmIsReadFalse(UUID companyId, Long empId);

    /* 전체 읽음 처리 */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE CommonAlarm a SET a.alarmIsRead = true WHERE a.companyId = :companyId AND a.alarmEmpId = :empId AND a.alarmIsRead = false")
    void markAllAsRead(@Param("companyId") UUID companyId, @Param("empId") Long empId);

    /* 전체 삭제 */
    void deleteByCompanyIdAndAlarmEmpId(UUID companyId, Long empId);

    /*최근 알람 5건 조회 - 읽음/안읽음 여부 포함 */
    List<CommonAlarm> findTop5ByCompanyIdAndAlarmEmpIdOrderByCreatedAtDesc(UUID companyId, Long empId);

}
