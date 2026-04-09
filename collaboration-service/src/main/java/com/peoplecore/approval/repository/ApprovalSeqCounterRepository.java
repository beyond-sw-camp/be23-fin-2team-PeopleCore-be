package com.peoplecore.approval.repository;

import com.peoplecore.approval.entity.ApprovalSeqCounter;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.hibernate.LockMode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalSeqCounterRepository extends JpaRepository<ApprovalSeqCounter, Long> {
    @Lock(LockModeType.OPTIMISTIC)
    Optional<ApprovalSeqCounter> findByCompanyIdAndSeqRuleIdAndSeqResetKey(UUID companyId, Long seqRuleId, String seqResetKey);

    //    비관적 락
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ApprovalSeqCounter  s " + "where s.companyId = :companyId " + "AND s.seqRuleId = :ruleId " + "AND s.seqResetKey = :resetKey")
    Optional<ApprovalSeqCounter> findWithLock(@Param("companyId") UUID companyId, @Param("ruleId") Long ruleId, @Param("resetKey") String resetKey);


}
