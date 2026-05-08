package com.peoplecore.approval.repository;

import com.peoplecore.approval.entity.ApprovalDelegation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
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

    /*관리자용 - 회사 전체 위임 목록 조회 */
    List<ApprovalDelegation> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    /*관리자용 - 위임 단건 조회 (empId 검증 없이) */
    Optional<ApprovalDelegation> findByAppDeleIdAndCompanyId(Long appDeleId, UUID companyId);

    /** 결재선 일괄 활성 위임 조회 — INBOX/알림 수신자 확장 시 N+1 방지용 */
    @Query("SELECT d FROM ApprovalDelegation d " +
            "WHERE d.companyId = :companyId AND d.empId IN :empIds " +
            "AND d.isActive = true AND d.startAt <= :date AND d.endAt >= :date")
    List<ApprovalDelegation> findActiveByEmps(@Param("companyId") UUID companyId,
                                              @Param("empIds") Collection<Long> empIds,
                                              @Param("date") LocalDate date);

    /** 대리자 입장 활성 위임 목록 — 대리 결재 권한 검증 시 사용 */
    @Query("SELECT d FROM ApprovalDelegation d " +
            "WHERE d.companyId = :companyId AND d.deleEmpId = :deleEmpId " +
            "AND d.isActive = true AND d.startAt <= :date AND d.endAt >= :date")
    List<ApprovalDelegation> findActiveByDelegate(@Param("companyId") UUID companyId,
                                                  @Param("deleEmpId") Long deleEmpId,
                                                  @Param("date") LocalDate date);
}
