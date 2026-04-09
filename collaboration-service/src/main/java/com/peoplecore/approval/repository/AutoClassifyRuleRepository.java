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
    List<AutoClassifyRule> findByCompanyIdAndEmpIdOrderBySortOrder(UUID companyId, Long empId);

    /** 단건 조회 (사원 격리) */
    Optional<AutoClassifyRule> findByRuleIdAndCompanyIdAndEmpId(Long ruleId, UUID companyId, Long empId);

    /** 부서 내 최대 sortOrder 조회 */
    @Query("SELECT COALESCE(MAX(r.sortOrder), 0) FROM AutoClassifyRule r WHERE r.companyId = :companyId AND r.empId = :empId")
    Integer findMaxSortOrder(@Param("companyId") UUID companyId, @Param("empId") Long empId);

    /** 자동분류 실행용: 사원의 활성 규칙 목록 */
    List<AutoClassifyRule> findByCompanyIdAndEmpIdAndIsActiveTrueOrderBySortOrder(UUID companyId, Long empId);
}
