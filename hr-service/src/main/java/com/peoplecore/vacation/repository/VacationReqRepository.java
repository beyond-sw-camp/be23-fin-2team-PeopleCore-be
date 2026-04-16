package com.peoplecore.vacation.repository;

import com.peoplecore.attendance.dto.VacationSlice;
import com.peoplecore.vacation.entity.VacationReq;
import com.peoplecore.vacation.entity.VacationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/* 휴가 신청 Repo */
@Repository
public interface VacationReqRepository extends JpaRepository<VacationReq, Long> {

    /**
     * 회사 + vacReqId 단건 조회
     */
    Optional<VacationReq> findByCompanyIdAndVacReqId(UUID companyId, Long vacReqId);

    /**
     * companyId + approvalDocId 단건 조회 — docCreated 중복 방지용
     */
    Optional<VacationReq> findByCompanyIdAndApprovalDocId(UUID companyId, Long approvalDocId);


    /*
     * 사원의 주 구간과 교집합되는 승인 휴가 — DTO projection 으로 필요 컬럼만 선택.
     */
    @Query("""
            SELECT new com.peoplecore.attendance.dto.VacationSlice(
              v.vacReqStartat, v.vacReqEndat, v.vacReqUseDay)
              FROM VacationReq v
             WHERE v.companyId = :companyId
               AND v.employee.empId = :empId
               AND v.vacReqStatus = :status
               AND v.vacReqStartat <= :weekEnd
               AND v.vacReqEndat >= :weekStart
            """)
    List<VacationSlice> findApprovedSlicesInWeek(@Param("companyId") UUID companyId,
                                                 @Param("empId") Long empId,
                                                 @Param("status") VacationStatus status,
                                                 @Param("weekStart") LocalDateTime weekStart,
                                                 @Param("weekEnd") LocalDateTime weekEnd);
}
