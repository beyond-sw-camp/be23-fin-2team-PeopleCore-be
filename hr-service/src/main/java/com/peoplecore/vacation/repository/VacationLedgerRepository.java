package com.peoplecore.vacation.repository;

import com.peoplecore.vacation.entity.LedgerEventType;
import com.peoplecore.vacation.entity.VacationLedger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/* 잔여 변동 이력 레포 - append-only */
@Repository
public interface VacationLedgerRepository extends JpaRepository<VacationLedger, Long> {

    /*
     * 잔여별 변동 이력 시간순 조회
     * 용도: 사원/관리자 화면 "이 잔여 변동 내역 보기"
     * 정렬: createdAt 오름차순 (오래된 → 최신)
     * 인덱스: idx_vacation_ledger_balance_created
     */
    List<VacationLedger> findAllByCompanyIdAndVacationBalance_BalanceIdOrderByCreatedAtAsc(
            UUID companyId, Long balanceId);

    /*
     * 사원 변동 이력 페이지 조회
     * 용도: 사원 화면 "내 휴가 변동 전체 이력" (전 유형 통합)
     * 정렬: createdAt 내림차순 (최신순)
     */
    Page<VacationLedger> findAllByCompanyIdAndEmpIdOrderByCreatedAtDesc(
            UUID companyId, Long empId, Pageable pageable);

    /*
     * 참조 추적 조회 - USED/RESTORED 가 어느 VacationRequest 인지
     * 용도: 신청 → 변동 이력 추적, 정합성 검증
     * 인덱스: idx_vacation_ledger_ref
     */
    List<VacationLedger> findAllByCompanyIdAndRefTypeAndRefId(
            UUID companyId, String refType, Long refId);

    /*
     * 잔여별 이벤트 타입별 changeDays 합산
     * 용도: 정합성 검증 잡 (Balance.totalDays = SUM(credit) - SUM(|debit|))
     * 반환: SUM 값. 행 없으면 0 반환 (COALESCE)
     */
    @Query("""
            SELECT COALESCE(SUM(l.changeDays), 0)
              FROM VacationLedger l
             WHERE l.companyId = :companyId
               AND l.vacationBalance.balanceId = :balanceId
               AND l.eventType = :eventType
            """)
    BigDecimal sumChangeDaysByBalanceAndEvent(@Param("companyId") UUID companyId,
                                              @Param("balanceId") Long balanceId,
                                              @Param("eventType") LedgerEventType eventType);
}