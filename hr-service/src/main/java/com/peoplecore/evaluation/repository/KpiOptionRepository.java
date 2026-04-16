package com.peoplecore.evaluation.repository;

import com.peoplecore.evaluation.domain.KpiOption;
import org.springframework.data.jpa.repository.JpaRepository;

// KPI 옵션 리포지토리
public interface KpiOptionRepository extends JpaRepository<KpiOption, Long> {
}
