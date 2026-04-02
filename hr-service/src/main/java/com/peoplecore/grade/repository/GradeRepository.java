package com.peoplecore.grade.repository;

import com.peoplecore.grade.domain.Grade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GradeRepository extends JpaRepository<Grade, Long> {
    boolean existsByGradeName(String gradeName);
    boolean existsByGradeCode(String gradeCode);
    List<Grade> findAllByOrderByGradeOrderAsc();
}
