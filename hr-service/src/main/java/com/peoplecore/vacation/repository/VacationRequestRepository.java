package com.peoplecore.vacation.repository;

import com.peoplecore.vacation.entity.RequestStatus;
import com.peoplecore.vacation.entity.VacationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/* 휴가 신청 레포 - 단순 메서드만. 복잡 조회는 VacationRequestQueryRepository (QueryDSL) */
@Repository
public interface VacationRequestRepository extends JpaRepository<VacationRequest, Long> {

    /*
     * 회사 + requestId 단건 조회
     * 용도: Kafka 결재 결과 수신 시, 화면 상세 조회
     * 인덱스: PK
     */
    Optional<VacationRequest> findByCompanyIdAndRequestId(UUID companyId, Long requestId);

    /*
     * 회사 + approvalDocId 그룹 조회 - 한 결재문서에 묶인 모든 슬롯 반환
     * 용도: Kafka docCreated 중복 수신 방어 / 결재 결과 반영 / 취소 시 그룹 일괄 처리
     * 인덱스: idx_vacation_request_approval_doc
     * 반환: 비어있으면 중복수신 아님 (insert 진행), 비어있지 않으면 기존 그룹 존재
     */
    List<VacationRequest> findByCompanyIdAndApprovalDocId(UUID companyId, Long approvalDocId);

    /*
     * 특정 휴가 유형을 참조하는 신청 존재 여부
     * 용도: 휴가 유형 물리 삭제 시 FK 참조 체크
     * 반환: true 면 삭제 차단 (VACATION_TYPE_IN_USE)
     */
    boolean existsByVacationType_TypeId(Long typeId);

    /*
     * 특정 사원 + 유형 + 기간(연 단위) PENDING/APPROVED 누적 일수 집계
     * 용도: 이벤트 기반 휴가 신청 시 한도 예상 검증 (기존 + 진행 중 + 이번 요청 ≤ cap)
     * 조건: requestStartAt 이 연도 범위 안에 속하고, 상태가 지정된 것들
     * 반환: 합계. 기존 건 없으면 0
     */
    @Query("""
            SELECT COALESCE(SUM(r.requestUseDays), 0)
              FROM VacationRequest r
             WHERE r.companyId = :companyId
               AND r.employee.empId = :empId
               AND r.vacationType.typeId = :typeId
               AND r.requestStatus IN (:statuses)
               AND r.requestStartAt >= :yearStart
               AND r.requestStartAt <  :nextYearStart
            """)
    BigDecimal sumDaysByStatuses(@Param("companyId") UUID companyId,
                                 @Param("empId") Long empId,
                                 @Param("typeId") Long typeId,
                                 @Param("statuses") java.util.List<RequestStatus> statuses,
                                 @Param("yearStart") LocalDateTime yearStart,
                                 @Param("nextYearStart") LocalDateTime nextYearStart);
}