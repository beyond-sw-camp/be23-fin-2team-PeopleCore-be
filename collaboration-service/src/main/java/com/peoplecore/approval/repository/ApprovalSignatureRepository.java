package com.peoplecore.approval.repository;

import com.peoplecore.approval.entity.ApprovalSignature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalSignatureRepository extends JpaRepository<ApprovalSignature, Long> {
    /*사원 서명 이력 조회 */
    Optional<ApprovalSignature> findByCompanyIdAndSigEmpId(UUID companyId, Long sigEmpId);

    /*결재선 서명 일괄 조회*/
    List<ApprovalSignature> findByCompanyIdAndSigEmpIdIn(UUID companyId, List<Long> sigEmpIds);

    /*사원 서명 이력 삭제*/
    @Modifying(clearAutomatically = true)
    void deleteByCompanyIdAndSigEmpId(UUID companyId, Long sigEmpId);
}
