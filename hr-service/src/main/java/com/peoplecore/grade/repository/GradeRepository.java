package com.peoplecore.grade.repository;

import com.peoplecore.grade.domain.Grade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GradeRepository extends JpaRepository<Grade, Long> {
    List<Grade> findAllByCompanyIdOrderByGradeOrderAsc(UUID companyId);
    boolean existsByGradeNameAndCompanyId(String gradeName, UUID companyId);
    long countByCompanyId(UUID companyId);

    Optional<Grade> findByGradeName(String gradeName);

    Optional<Grade> findByCompanyIdAndGradeName(UUID companyId, String gradeName);
}
