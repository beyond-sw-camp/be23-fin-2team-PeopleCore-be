package com.peoplecore.attendance.repository;

import com.peoplecore.attendance.entity.CommuteRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
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
public interface CommuteRecordRepository extends JpaRepository<CommuteRecord, Long> {

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




}
