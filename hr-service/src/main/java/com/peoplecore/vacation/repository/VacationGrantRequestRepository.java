package com.peoplecore.vacation.repository;

import com.peoplecore.vacation.entity.RequestStatus;
import com.peoplecore.vacation.entity.VacationGrantRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/* 휴가 부여 신청 레포 - 단순 메서드만. 복잡 조회 늘면 QueryDSL Repository 분리 */
@Repository
public interface VacationGrantRequestRepository extends JpaRepository<VacationGrantRequest, Long> {

    /*
     * 회사 + requestId 단건 조회
     * 용도: Kafka 결재 결과 수신 시, 상세 조회
     * 인덱스: PK
     */
    Optional<VacationGrantRequest> findByCompanyIdAndRequestId(UUID companyId, Long requestId);

    /*
     * 회사 + approvalDocId 단건 조회
     * 용도: Kafka grantDocCreated 중복 수신 방어 (같은 결재 문서 두 번 INSERT 방지)
     * 인덱스: idx_vgr_approval_doc
     */
    Optional<VacationGrantRequest> findByCompanyIdAndApprovalDocId(UUID companyId, Long approvalDocId);

    /*
     * 특정 휴가 유형을 참조하는 부여 신청 존재 여부
     * 용도: 휴가 유형 물리 삭제 시 FK 참조 체크
     * 반환: true 면 삭제 차단 (VACATION_TYPE_IN_USE)
     */
    boolean existsByVacationType_TypeId(Long typeId);

    /*
     * 특정 사원 + 유형 + 신청 연도 지정 상태 누적 부여 일수 합 (cap 검증용)
     * 조건: createdAt 이 연도 범위 안 + 상태가 지정된 것
     * 반환: 합계. 기존 건 없으면 0
     * 주의: GRANT 는 기간 필드(start/end) 가 없어 createdAt 기준으로 연도 판정
     */
    @Query("""
            SELECT COALESCE(SUM(g.requestUseDays), 0)
              FROM VacationGrantRequest g
             WHERE g.companyId = :companyId
               AND g.employee.empId = :empId
               AND g.vacationType.typeId = :typeId
               AND g.requestStatus IN (:statuses)
               AND g.createdAt >= :yearStart
               AND g.createdAt <  :nextYearStart
            """)
    BigDecimal sumDaysByStatuses(@Param("companyId") UUID companyId,
                                 @Param("empId") Long empId,
                                 @Param("typeId") Long typeId,
                                 @Param("statuses") List<RequestStatus> statuses,
                                 @Param("yearStart") LocalDateTime yearStart,
                                 @Param("nextYearStart") LocalDateTime nextYearStart);

    /*
     * 사원 본인 부여 신청 이력 페이지 (신청일 내림차순)
     * 용도: 부여 내역 화면
     * fetch join: vacationType 만 (Employee 는 이미 본인이라 불필요)
     */
    @Query(value = """
            SELECT g FROM VacationGrantRequest g
              JOIN FETCH g.vacationType
             WHERE g.companyId = :companyId
               AND g.employee.empId = :empId
             ORDER BY g.createdAt DESC
            """,
            countQuery = """
            SELECT COUNT(g) FROM VacationGrantRequest g
             WHERE g.companyId = :companyId
               AND g.employee.empId = :empId
            """)
    Page<VacationGrantRequest> findEmployeeHistory(@Param("companyId") UUID companyId,
                                                   @Param("empId") Long empId,
                                                   Pageable pageable);
}
