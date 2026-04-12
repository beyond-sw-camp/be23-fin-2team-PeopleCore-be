package com.peoplecore.evaluation.repository;

import com.peoplecore.evaluation.domain.Calibration;
import org.springframework.data.jpa.repository.JpaRepository;

// 보정이력 리포지토리
public interface CalibrationRepository extends JpaRepository<Calibration, Long> {
}
