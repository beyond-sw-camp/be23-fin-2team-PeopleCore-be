package com.peoplecore.attendance.repository;

import com.peoplecore.attendance.entity.AttendanceModify;
import com.peoplecore.attendance.entity.ModifyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/*
 * 근태 정정 신청 Repository.
 */
@Repository
public interface AttendanceModifyRepository extends JpaRepository<AttendanceModify, Long> {

    /* 회사 + AttendanceModify PK 단건 — 상세 조회 시 회사 소속 검증 겸용 */
    Optional<AttendanceModify> findByCompanyIdAndAttenModiId(UUID companyId, Long attenModiId);

    /* 회사 + approvalDocId 단건 — Kafka result 이벤트 역조회 (idx_atten_modify_doc_id 히트) */
    Optional<AttendanceModify> findByCompanyIdAndApprovalDocId(UUID companyId, Long approvalDocId);

    /* docCreated Consumer + prefill 에서 PENDING 중복 검사 — 같은 사원/같은 날짜 다중 신청 차단 */
    /* 키 변경: comRecId 는 nullable 이라 같은 날에 record 있을 때/없을 때 충돌 검사 불가 → workDate 로 통일 */
    boolean existsByEmployee_EmpIdAndWorkDateAndAttenStatus(Long empId,
                                                            LocalDate workDate,
                                                            ModifyStatus attenStatus);

    /* HR 관리자 목록 — 상태 필터 (idx_atten_modify_company_status 히트) */
    Page<AttendanceModify> findByCompanyIdAndAttenStatus(UUID companyId,
                                                        ModifyStatus attenStatus,
                                                        Pageable pageable);

    /* HR 관리자 "전체" 탭 */
    Page<AttendanceModify> findByCompanyId(UUID companyId, Pageable pageable);

    /*  본인 신청 이력 (idx_atten_modify_emp 히트) */
    Page<AttendanceModify> findByEmployee_EmpId(Long empId, Pageable pageable);
}