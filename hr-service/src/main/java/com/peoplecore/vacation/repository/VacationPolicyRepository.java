package com.peoplecore.vacation.repository;

import com.peoplecore.vacation.entity.VacationPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VacationPolicyRepository extends JpaRepository<VacationPolicy, Long> {
    /*회사 If로 정책 단건 조회 */
    Optional<VacationPolicy> findByCompanyId(UUID companyId);

    /** 회사 ID 로 정책 전체 조회 (중복 탐지/진단용) */
    List<VacationPolicy> findAllByCompanyId(UUID companyId);

    /** 정책 존재 여부 (initDefault 멱등성 체크) */
    boolean existsByCompanyId(UUID companyId);

    /**
     * 회사 ID 로 정책 + 연차 발생 규칙 한 번에 조회 (N+1 방지)
     * - LEFT JOIN FETCH: 규칙 0건이어도 정책은 반환
     * - DISTINCT: 규칙 다건 join 으로 인한 정책 중복 row 제거
     */
    @Query("""
           SELECT DISTINCT p FROM VacationPolicy p
           LEFT JOIN FETCH p.createRules
           WHERE p.companyId = :companyId
           """)
    List<VacationPolicy> findAllByCompanyIdFetchRules(@Param("companyId") UUID companyId);

}


