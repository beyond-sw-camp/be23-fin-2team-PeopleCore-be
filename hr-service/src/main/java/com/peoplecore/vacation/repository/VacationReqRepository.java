package com.peoplecore.vacation.repository;

import com.peoplecore.vacation.entity.VacationReq;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/* 휴가 신청 Repo */
@Repository
public interface VacationReqRepository extends JpaRepository<VacationReq, Long> {

    /** 회사 + vacReqId 단건 조회 (Kafka 라우팅 검증) */
    Optional<VacationReq> findByCompanyIdAndVacReqId(UUID companyId, Long vacReqId);
}
