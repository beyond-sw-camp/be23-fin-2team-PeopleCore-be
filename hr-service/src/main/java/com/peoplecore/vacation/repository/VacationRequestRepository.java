package com.peoplecore.vacation.repository;

import com.peoplecore.vacation.entity.VacationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
     * 회사 + approvalDocId 단건 조회
     * 용도: Kafka docCreated 중복 수신 방어 (같은 결재 문서 두 번 INSERT 방지)
     * 인덱스: idx_vacation_request_approval_doc
     */
    Optional<VacationRequest> findByCompanyIdAndApprovalDocId(UUID companyId, Long approvalDocId);
}