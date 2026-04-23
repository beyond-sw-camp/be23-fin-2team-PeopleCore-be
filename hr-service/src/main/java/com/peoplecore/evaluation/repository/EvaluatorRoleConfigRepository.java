package com.peoplecore.evaluation.repository;

import com.peoplecore.evaluation.domain.EvaluatorRoleConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

// 회사당 1행. uk_evaluator_role_company 제약으로 findByCompanyId 가 0..1 보장.
public interface EvaluatorRoleConfigRepository extends JpaRepository<EvaluatorRoleConfig, Long> {
    Optional<EvaluatorRoleConfig> findByCompanyId(UUID companyId);
}
