package com.peoplecore.attendance.repository;

import com.peoplecore.attendance.entity.CommuteRecord;
import com.peoplecore.attendance.entity.CommuteRecordId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/*
 * 출퇴근 기록 Repository.
 * PK 가 복합키 (comRecId, workDate) → JpaRepository<..., CommuteRecordId>.
 */
public interface CommuteRecordRepository extends JpaRepository<CommuteRecord, CommuteRecordId> {

    /*
     * 특정 회사/사원/근무일자 기록 1건 조회.
     * - 체크인 중복 방지: 오늘자 있으면 409
     * - 체크아웃: 기존 레코드 로드 후 dirty checking
     * (company_id, emp_id, work_date) 복합 인덱스 커버.
     */
    Optional<CommuteRecord> findByCompanyIdAndEmployee_EmpIdAndWorkDate(
            UUID companyId, Long empId, LocalDate workDate);

    /* 자정 넘김 체크아웃 지원용 조회
     * workDate가 from,to 범위 + comRecChekOut Is NULL (아직 퇴근 안찍은 )레코드
     *
     * workDate between조건으로 최대 2개 파티션만 스캔 */
    Optional<CommuteRecord> findFirstByCompanyIdAndEmployee_EmpIdAndWorkDateBetweenAndComRecCheckOutIsNullOrderByWorkDateDesc( UUID companyId, Long empId, LocalDate from, LocalDate to);
}