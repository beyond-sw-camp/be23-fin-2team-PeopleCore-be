package com.peoplecore.attendance.repository;

import com.peoplecore.attendance.entity.CommuteRecord;
import com.peoplecore.attendance.entity.WorkStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
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

    /* 근태 정정 승인 native UPDATE - check-in/out + workStatus 일괄 교체.
     * workStatus 는 호출부(AttendanceModifyService)에서 새 시간 기준으로 산출해 전달.
     * 파티션 프루닝: WHERE work_date 포함 필수. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE commute_record
               SET com_rec_check_in  = :newCheckIn,
                   com_rec_check_out = :newCheckOut,
                   work_status       = :newStatus
             WHERE com_rec_id = :comRecId
               AND work_date  = :workDate
            """, nativeQuery = true)
    int applyAttendanceModify(@Param("comRecId") Long comRecId,
                              @Param("workDate") LocalDate workDate,
                              @Param("newCheckIn") LocalDateTime newCheckIn,
                              @Param("newCheckOut") LocalDateTime newCheckOut,
                              @Param("newStatus") String newStatus);

    /* CommuteRecord 분 컬럼 native UPDATE - JPA 더티체킹 회피.
     * 파티션 프루닝: WHERE work_date 포함 필수.
     * 호출부: PayrollMinutesCalculator.applyApprovedRecognition. */
    @Modifying
    @Query(value = """
            UPDATE commute_record
               SET actual_work_minutes         = :actual,
                   overtime_minutes            = :overtime,
                   unrecognized_ot_minutes     = :unrecognizedOt,
                   recognized_extended_minutes = :recExt,
                   recognized_night_minutes    = :recNight,
                   recognized_holiday_minutes  = :recHoliday
             WHERE com_rec_id = :comRecId
               AND work_date  = :workDate
            """, nativeQuery = true)
    int applyPayrollMinutes(@Param("comRecId") Long comRecId,
                            @Param("workDate") LocalDate workDate,
                            @Param("actual") Long actual,
                            @Param("overtime") Long overtime,
                            @Param("unrecognizedOt") Long unrecognizedOt,
                            @Param("recExt") Long recExt,
                            @Param("recNight") Long recNight,
                            @Param("recHoliday") Long recHoliday);

    /* 배치 자동마감 native UPDATE - 8 컬럼 (checkOut/status + 6 분 컬럼) 일괄.
     * 가드 (체크인 있음 + 미체크아웃) 를 WHERE 에 박아 atomic check-and-update.
     * 영향행 0 이면 가드 위반 또는 이미 처리됨.
     * 파티션 프루닝: WHERE work_date 포함 필수. */
    @Modifying
    @Query(value = """
            UPDATE commute_record
               SET com_rec_check_out           = :closedAt,
                   work_status                 = 'AUTO_CLOSED',
                   actual_work_minutes         = 0,
                   overtime_minutes            = 0,
                   unrecognized_ot_minutes     = 0,
                   recognized_extended_minutes = 0,
                   recognized_night_minutes    = 0,
                   recognized_holiday_minutes  = 0
             WHERE com_rec_id = :comRecId
               AND work_date  = :workDate
               AND com_rec_check_in IS NOT NULL
               AND com_rec_check_out IS NULL
            """, nativeQuery = true)
    int applyAutoClose(@Param("comRecId") Long comRecId,
                       @Param("workDate") LocalDate workDate,
                       @Param("closedAt") LocalDateTime closedAt);

    /* 퇴근 체크아웃 native UPDATE - 9 컬럼 (checkOut/IP/status + 6 분 컬럼) 일괄.
     * 가드 (체크인 있음 + 미체크아웃) 를 WHERE 에 박아 atomic check-and-update.
     * 영향행 0 이면 가드 위반 (이미 체크아웃됨 등). 파티션 프루닝: WHERE work_date 포함 필수. */
    @Modifying
    @Query(value = """
            UPDATE commute_record
               SET com_rec_check_out           = :checkOut,
                   check_out_ip                = :ip,
                   work_status                 = :status,
                   actual_work_minutes         = :actual,
                   overtime_minutes            = :overtime,
                   unrecognized_ot_minutes     = :unrecognizedOt,
                   recognized_extended_minutes = :recExt,
                   recognized_night_minutes    = :recNight,
                   recognized_holiday_minutes  = :recHoliday
             WHERE com_rec_id = :comRecId
               AND work_date  = :workDate
               AND com_rec_check_in IS NOT NULL
               AND com_rec_check_out IS NULL
            """, nativeQuery = true)
    int applyCheckOut(@Param("comRecId") Long comRecId,
                      @Param("workDate") LocalDate workDate,
                      @Param("checkOut") LocalDateTime checkOut,
                      @Param("ip") String ip,
                      @Param("status") String status,
                      @Param("actual") Long actual,
                      @Param("overtime") Long overtime,
                      @Param("unrecognizedOt") Long unrecognizedOt,
                      @Param("recExt") Long recExt,
                      @Param("recNight") Long recNight,
                      @Param("recHoliday") Long recHoliday);

    /*
     * 특정 (comRecId, workDate) CommuteRecord 단건 조회 — 파티션 프루닝 보장.
     * 용도: AttendanceModifyService 가 승인 처리 전에 현재값/역전 검증용으로 load.
     */
    Optional<CommuteRecord> findByComRecIdAndWorkDate(Long comRecId, LocalDate workDate);

    /* 주간 actualWorkMinutes 합 - OT/정정 한도 검증용. work_date 범위라 파티션 프루닝 */
    @Query("""
            SELECT COALESCE(SUM(c.actualWorkMinutes), 0)
              FROM CommuteRecord c
             WHERE c.companyId = :companyId
               AND c.employee.empId = :empId
               AND c.workDate BETWEEN :start AND :end
            """)
    Long sumActualWorkMinutesInWeek(@Param("companyId") UUID companyId,
                                    @Param("empId") Long empId,
                                    @Param("start") LocalDate start,
                                    @Param("end") LocalDate end);

    /* 정정 대상 일자 제외 주간 합 - 정정 적용 후 합계 추정용 */
    @Query("""
            SELECT COALESCE(SUM(c.actualWorkMinutes), 0)
              FROM CommuteRecord c
             WHERE c.companyId = :companyId
               AND c.employee.empId = :empId
               AND c.workDate BETWEEN :start AND :end
               AND c.workDate <> :exclude
            """)
    Long sumActualWorkMinutesInWeekExcluding(@Param("companyId") UUID companyId,
                                             @Param("empId") Long empId,
                                             @Param("start") LocalDate start,
                                             @Param("end") LocalDate end,
                                             @Param("exclude") LocalDate exclude);


    /*
     * 사원의 [from, to] 구간 출근일 LocalDate 리스트 - check_in 존재 row 만.
     * 용도: AttendanceCheckService 만근 판정 (영업일 vs 출근일+휴가일 비교).
     * 파티션 프루닝: workDate BETWEEN 조건으로 해당 파티션만 스캔.
     */
    @Query("""
            SELECT c.workDate FROM CommuteRecord c
             WHERE c.companyId = :companyId
               AND c.employee.empId = :empId
               AND c.workDate BETWEEN :from AND :to
               AND c.comRecCheckIn IS NOT NULL
            """)
    List<LocalDate> findAttendedDatesByEmpAndPeriod(@Param("companyId") UUID companyId,
                                                    @Param("empId") Long empId,
                                                    @Param("from") LocalDate from,
                                                    @Param("to") LocalDate to);


//     사원의 workStatus 카운트.
    long countByCompanyIdAndEmployee_EmpIdAndWorkDateBetweenAndWorkStatusIn(
            UUID companyId, Long empId, LocalDate from, LocalDate to,
            Collection<WorkStatus> statuses);
}
