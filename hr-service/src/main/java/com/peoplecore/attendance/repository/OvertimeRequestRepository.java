package com.peoplecore.attendance.repository;

import com.peoplecore.attendance.entity.OvertimeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/* 초과 근무 신청 Repo */
@Repository
public interface OvertimeRequestRepository extends JpaRepository<OvertimeRequest, Long> {

    /** 회사 + otId 단건 조회 (Kafka 라우팅 검증) */
    Optional<OvertimeRequest> findByCompanyIdAndOtId(UUID companyId, Long otId);

    /** companyId + approvalDocId 단건 조회 — docCreated 중복 방지, result 이벤트 매칭용 */
    Optional<OvertimeRequest> findByCompanyIdAndApprovalDocId(UUID companyId, Long approvalDocId);

    /** 사원 + 날짜 범위 + APPROVED — 체크아웃 시 사전 승인 OT 조회 */
    @Query("""
            SELECT o FROM OvertimeRequest o
             WHERE o.employee.empId = :empId
               AND o.otStatus = com.peoplecore.attendance.entity.OtStatus.APPROVED
               AND o.otDate BETWEEN :dayStart AND :dayEnd
            """)
    List<OvertimeRequest> findApprovedByEmpAndDateRange(Long empId,
                                                       LocalDateTime dayStart,
                                                       LocalDateTime dayEnd);

    /** 사원 + 주 범위 PENDING/APPROVED 분 합계. JPQL TIMESTAMPDIFF 미지원 → native */
    @Query(value = """
            SELECT COALESCE(SUM(TIMESTAMPDIFF(MINUTE, o.ot_plan_start, o.ot_plan_end)), 0)
              FROM overtime_request o
             WHERE o.emp_id = :empId
               AND o.ot_status IN ('PENDING', 'APPROVED')
               AND o.ot_date BETWEEN :weekStart AND :weekEnd
            """, nativeQuery = true)
    Long sumPendingApprovedMinutesInWeek(Long empId,
                                         LocalDateTime weekStart,
                                         LocalDateTime weekEnd);

    /** 사원 + 주 범위 DRAFT 제외 이력 — otDate ASC, otPlanStart ASC */
    @Query("""
            SELECT o FROM OvertimeRequest o
             WHERE o.employee.empId = :empId
               AND o.otDate BETWEEN :weekStart AND :weekEnd
             ORDER BY o.otDate ASC, o.otPlanStart ASC
            """)
    List<OvertimeRequest> findWeekHistoryByEmp(Long empId,
                                               LocalDateTime weekStart,
                                               LocalDateTime weekEnd);
}
