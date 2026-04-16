package com.peoplecore.attendance.repository;

import com.peoplecore.attendance.entity.CommuteRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/*
 * 출퇴근 기록 Repository.
 *
 * JPA 매핑상 PK 는 단일 Long (com_rec_id).
 * DB 레벨 복합 PK (com_rec_id, work_date) 는 파티셔닝용으로 Initializer 가 재정의.
 * 비즈니스 유일성 (company_id, emp_id, work_date) 은 UNIQUE 제약으로 DB 가 보장.
 */
@Repository
public interface CommuteRecordRepository extends JpaRepository<CommuteRecord, Long> {

    /**
     * 사원의 [startDate, endDate] 구간에서 인정 수당(연장/야간/휴일) 이 붙은 CommuteRecord 리스트.
     */
    @Query("""
            SELECT c FROM CommuteRecord c
             WHERE c.employee.empId = :empId
               AND c.workDate BETWEEN :startDate AND :endDate
               AND (c.recognizedExtendedMinutes > 0
                 OR c.recognizedNightMinutes > 0
                 OR c.recognizedHolidayMinutes > 0)
            """)
    List<CommuteRecord> findRecognizedByMonth(@Param("empId") Long empId,
                                              @Param("startDate") LocalDate startDate,
                                              @Param("endDate") LocalDate endDate);


    /*
     * 특정 회사/사원/근무일자 기록 1건 조회.
     */
    Optional<CommuteRecord> findByCompanyIdAndEmployee_EmpIdAndWorkDate(
            UUID companyId, Long empId, LocalDate workDate);

    /*
     * 자정 넘김 체크아웃 지원용 조회.
     */
    Optional<CommuteRecord>
    findFirstByCompanyIdAndEmployee_EmpIdAndWorkDateBetweenAndComRecCheckOutIsNullOrderByWorkDateDesc(
            UUID companyId, Long empId, LocalDate from, LocalDate to);

    /*
     * 사원의 [from, to] 구간 출퇴근 기록 페이지 — workDate DESC 정렬.

     */
    Page<CommuteRecord>
    findByCompanyIdAndEmployee_EmpIdAndWorkDateBetweenOrderByWorkDateDesc(
            UUID companyId, Long empId, LocalDate from, LocalDate to, Pageable pageable);

    /*
     * 사원의 [from, to] 구간 실근무 분 합계 — check-in, check-out 모두 존재할 때만 합산.
     * 사용: AttendanceAdminService.getEmployeeHistory (사원 일별 근무 현황 모달 주간 근무시간 헤더 카드)
     */
    @Query(value = """
            SELECT COALESCE(SUM(TIMESTAMPDIFF(MINUTE, c.com_rec_check_in, c.com_rec_check_out)), 0)
              FROM commute_record c
             WHERE c.company_id = :companyId
               AND c.emp_id = :empId
               AND c.work_date BETWEEN :from AND :to
               AND c.com_rec_check_in IS NOT NULL
               AND c.com_rec_check_out IS NOT NULL
            """, nativeQuery = true)
    Long sumWorkedMinutesBetween(@Param("companyId") UUID companyId,
                                 @Param("empId") Long empId,
                                 @Param("from") LocalDate from,
                                 @Param("to") LocalDate to);

    /* 근태 정정 승인 native UPDATE — check-in/out 교체 + is_auto_closed 해제 일괄.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE commute_record
               SET com_rec_check_in  = :newCheckIn,
                   com_rec_check_out = :newCheckOut,
                   is_auto_closed    = false
             WHERE com_rec_id = :comRecId
               AND work_date  = :workDate
            """, nativeQuery = true)
    int applyAttendanceModify(@Param("comRecId") Long comRecId,
                              @Param("workDate") LocalDate workDate,
                              @Param("newCheckIn") LocalDateTime newCheckIn,
                              @Param("newCheckOut") LocalDateTime newCheckOut);

    /*
     * 특정 (comRecId, workDate) CommuteRecord 단건 조회 — 파티션 프루닝 보장.
     * 용도: AttendanceModifyService 가 승인 처리 전에 현재값/역전 검증용으로 load.
     */
    Optional<CommuteRecord> findByComRecIdAndWorkDate(Long comRecId, LocalDate workDate);
}
