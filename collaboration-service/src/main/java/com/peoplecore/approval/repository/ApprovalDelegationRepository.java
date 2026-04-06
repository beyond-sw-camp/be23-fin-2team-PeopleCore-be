package com.peoplecore.approval.repository;

import com.peoplecore.approval.entity.ApprovalDelegation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalDelegationRepository extends JpaRepository<ApprovalDelegation, Long> {
    /*내 위임 목록 조회 */
    List<ApprovalDelegation> findByCompanyIdAndEmpIdOrderByCreatedAtDesc(UUID companyId, Long empId);

    /* 본인 위임 단건 조회(삭제, 토글용) */
    Optional<ApprovalDelegation> findByAppDeleIdAndCompanyIdAndEmpId(Long appDeleId, UUID companyId, Long empId);

    /*중복 위임체크 - 기간 겹침 방지 */
    boolean existsByCompanyIdAndEmpIdAndIsActiveTrueAndStartAtLessThanEqualAndEndAtGreaterThanEqual(UUID companyId, Long empId, LocalDate endAt, LocalDate startAt);
}
