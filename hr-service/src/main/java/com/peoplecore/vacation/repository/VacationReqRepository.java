package com.peoplecore.vacation.repository;

import com.peoplecore.vacation.entity.VacationReq;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/* 휴가 신청 Repo */
@Repository
public interface VacationReqRepository extends JpaRepository<VacationReq, Long> {

    /** 회사 + vacReqId 단건 조회 */
    Optional<VacationReq> findByCompanyIdAndVacReqId(UUID companyId, Long vacReqId);

    /** companyId + approvalDocId 단건 조회 — docCreated 중복 방지용 */
    Optional<VacationReq> findByCompanyIdAndApprovalDocId(UUID companyId, Long approvalDocId);
}
