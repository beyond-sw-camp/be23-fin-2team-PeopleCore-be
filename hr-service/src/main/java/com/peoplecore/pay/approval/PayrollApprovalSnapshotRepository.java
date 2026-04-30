package com.peoplecore.pay.approval;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PayrollApprovalSnapshotRepository extends JpaRepository<PayrollApprovalSnapshot, Long> {

    Optional<PayrollApprovalSnapshot> findByApprovalDocId(Long approvalDocId);

}
