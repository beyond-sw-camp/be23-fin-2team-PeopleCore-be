package com.peoplecore.approval.repository;

import com.peoplecore.approval.entity.AutoClassifyRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AutoClassifyRuleRepository extends JpaRepository<AutoClassifyRule, Long> {

    /** 규칙 목록 (우선순위 정렬) */
    List<AutoClassifyRule> findByCompanyIdAndDeptIdOrderBySortOrder(UUID companyId, Long deptId);

    /** 단건 조회 (회사 격리) */
    Optional<AutoClassifyRule> findByRuleIdAndCompanyId(Long ruleId, UUID companyId);

    /** 부서 내 최대 sortOrder 조회 */
    @Query("SELECT COALESCE(MAX(r.sortOrder), 0) FROM AutoClassifyRule r WHERE r.companyId = :companyId AND r.deptId = :deptId")
    Integer findMaxSortOrder(@Param("companyId") UUID companyId, @Param("deptId") Long deptId);
}
