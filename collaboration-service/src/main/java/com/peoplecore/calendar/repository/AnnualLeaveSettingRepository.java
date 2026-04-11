package com.peoplecore.calendar.repository;

import com.peoplecore.calendar.entity.AnnualLeaveSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AnnualLeaveSettingRepository extends JpaRepository<AnnualLeaveSetting, Long> {

    List<AnnualLeaveSetting> findByCompanyIdAndEmpId(UUID companyId, Long empId);

    void deleteByCompanyIdAndEmpId(UUID companyId, Long empId);
}
