package com.peoplecore.attendance.repository;

import com.peoplecore.attendance.entity.CommuteRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 출퇴근 기록 Repository.

 * JPA 매핑상 PK 는 단일 Long (com_rec_id).
 * DB 레벨 복합 PK (com_rec_id, work_date) 는 파티셔닝용으로 Initializer 가 재정의.
 * 비즈니스 유일성 (company_id, emp_id, work_date) 은 UNIQUE 제약으로 DB 가 보장.
 */
public interface CommuteRecordRepository extends JpaRepository<CommuteRecord, Long> {

    /**
     * 특정 회사/사원/근무일자 기록 1건 조회.
     *  - 체크인 중복 방지: 오늘자 있으면 409
     *  - 체크아웃: 기존 레코드 로드 후 dirty checking
     * (company_id, emp_id, work_date) 복합 인덱스/UNIQUE 제약 커버.
     */
    Optional<CommuteRecord> findByCompanyIdAndEmployee_EmpIdAndWorkDate(
            UUID companyId, Long empId, LocalDate workDate);

    /**
     * 자정 넘김 체크아웃 지원용 조회.
     * workDate BETWEEN [from, to] + comRecCheckOut IS NULL (아직 퇴근 안 찍은) 레코드 중
     * workDate 최신 1건. BETWEEN 조건으로 최대 2개 파티션만 스캔 (파티션 프루닝 보장).
     *
     * 사용: CommuteService.checkOut — from=today.minusDays(1), to=today
     *  - 4/30 23:55 체크인 → 5/1 00:10 체크아웃: 4/30 레코드 매칭
     *  - 어제 까먹고 오늘 퇴근: 어제 open 레코드 매칭 (사실 기록 보존)
     */
    Optional<CommuteRecord>
    findFirstByCompanyIdAndEmployee_EmpIdAndWorkDateBetweenAndComRecCheckOutIsNullOrderByWorkDateDesc(
            UUID companyId, Long empId, LocalDate from, LocalDate to);


    /// 급여에서 해당 월 승인된 초과근무 기록 조회용

//    특정사원의 해당 월 - 인정된 초과근무 일별 기록 조회
//    recognized 3개 컬럼중 하나라도 > 0 인 날만 조회
    @Query("SELECT c FROM CommuteRecord c " +
            "WHERE c.employee.empId = :empId " +
            "AND c.workDate BETWEEN :startDate AND :endDate " +
            "AND (c.recognizedExtendedMinutes > 0 " +
            "OR c.recognizedNightMinutes > 0 " +
            "OR c.recognizedHolidayMinutes > 0 " +
            ") ORDER BY c.workDate")
    List<CommuteRecord> findRecognizedByMonth(
            @Param("empId") Long empId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

//    특정 사원의 해당 월 인정된 초과근무 분리 시간 합계 (월간 집계)
    @Query("SELECT new map(" +
            " SUM(c.recognizedExtendedMinutes) as totalExtendedMin," +
            " SUM(c.recognizedNightMinutes) as totalNightMin," +
            " SUM(c.recognizedHolidayMinutes) as totalHolidayMin" +
            ") FROM CommuteRecord c " +
            "WHERE c.employee.empId = :empId " +
            "AND c.workDate BETWEEN :startDate AND :endDate " +
            "AND (c.recognizedExtendedMinutes > 0 " +
            "OR c.recognizedNightMinutes > 0 " +
            "OR c.recognizedHolidayMinutes > 0)")
    Map<String, Object> sumRecognizedMinutesByMonth(
            @Param("empId") Long empId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
