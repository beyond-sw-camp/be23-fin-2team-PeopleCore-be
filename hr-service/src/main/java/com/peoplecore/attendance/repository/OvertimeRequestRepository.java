package com.peoplecore.attendance.repository;

import com.peoplecore.attendance.entity.OvertimeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
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

    /** 다수 사원 + 날짜 범위 + APPROVED — 배치 조회용 (N+1 회피) */
    @Query("""
            SELECT o FROM OvertimeRequest o
             WHERE o.employee.empId IN :empIds
               AND o.otStatus = com.peoplecore.attendance.entity.OtStatus.APPROVED
               AND o.otDate BETWEEN :dayStart AND :dayEnd
            """)
    List<OvertimeRequest> findApprovedByEmpIdsAndDateRange(@Param("empIds") Collection<Long> empIds,
                                                          @Param("dayStart") LocalDateTime dayStart,
                                                          @Param("dayEnd") LocalDateTime dayEnd);

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

    /** 사원 + 주 범위 미래 OT 분 합계 (PENDING/APPROVED 중 ot_plan_end 가 :now 이후인 것만).
     *  주간 한도 검증용 — 과거 OT 는 CommuteRecord.actualWorkMinutes 에 흡수되어 이중카운트 방지 위해 제외.
     *  JPQL TIMESTAMPDIFF 미지원 → native */
    @Query(value = """
            SELECT COALESCE(SUM(TIMESTAMPDIFF(MINUTE, o.ot_plan_start, o.ot_plan_end)), 0)
              FROM overtime_request o
             WHERE o.emp_id = :empId
               AND o.ot_status IN ('PENDING', 'APPROVED')
               AND o.ot_plan_end > :now
               AND o.ot_date BETWEEN :weekStart AND :weekEnd
            """, nativeQuery = true)
    Long sumFuturePlanMinutesInWeek(Long empId,
                                    LocalDateTime now,
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

    /**
     * 사원의 [from, to] 구간 APPROVED OT 의 일별 가장 이른 시작 시각.
     * 같은 날 여러 APPROVED 가 있으면 MIN(ot_plan_start).
     * native: DATE() 로 ot_date 에서 날짜만 꺼내 그룹핑.
     * 반환: [ [java.sql.Date workDate, Timestamp planStart], ... ]
     * 사용: 월간 요약의 overtimeStartAt 산출. 없는 날은 service 에서 groupEndTime 폴백.
     */
    @Query(value = """
            SELECT DATE(o.ot_date) AS work_date,
                   MIN(o.ot_plan_start) AS plan_start
              FROM overtime_request o
             WHERE o.emp_id = :empId
               AND o.ot_status = 'APPROVED'
               AND o.ot_date BETWEEN :from AND :to
             GROUP BY DATE(o.ot_date)
            """, nativeQuery = true)
    List<Object[]> findApprovedPlanStartByDate(@Param("empId") Long empId,
                                               @Param("from") LocalDateTime from,
                                               @Param("to") LocalDateTime to);
}
