package com.peoplecore.alarm.repository;

import com.peoplecore.entity.AlarmSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AlarmSettingsRepository extends JpaRepository<AlarmSettings, Long> {

//    사원 전체 알림 설정 목록
    List<AlarmSettings> findByCompanyIdAndEmpId(UUID companyId, Long EmpId);

//    특정 서비스의 알림설정조회 - AlarmService.createAndPush에서 사용
    Optional<AlarmSettings> findByCompanyIdAndEmpIdAndService(UUID companyId, Long empId, String service);
}
