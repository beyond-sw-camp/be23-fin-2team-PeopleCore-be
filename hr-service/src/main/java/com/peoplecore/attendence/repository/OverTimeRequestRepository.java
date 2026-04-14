package com.peoplecore.attendence.repository;

import com.peoplecore.attendance.entity.OtStatus;
import com.peoplecore.attendance.entity.OvertimeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OverTimeRequestRepository extends JpaRepository<OvertimeRequest, Long> {

//    특정 사원의 해당 월 승인된 초과근무 조회
    List<OvertimeRequest> findByEmpIdAndOtStatusAndOtDateBetween
    (        Long empId,
                OtStatus otStatus, LocalDateTime startOfMonth, LocalDateTime endOfMonth
    );

//    이미 급여대장에 적용된 초과근무 ID 조회(중복방지)
    @Query("SELECT pd.otId FROM PayrollDetails pd " +
            "WHERE pd.payrollRuns.payrollRunId = :payrollRunId " +
            "AND pd.employee.empId = :empId " +
            "AND pd.otId IS NOT NULL")
    List<Long> findAppliedOtIds(
            @Param("payrollRunId") Long payrollRunId,
            @Param("empId") Long empId
        );

    }
