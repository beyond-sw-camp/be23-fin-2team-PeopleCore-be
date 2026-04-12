package com.peoplecore.evaluation.repository;

import com.peoplecore.evaluation.domain.Season;
import org.springframework.data.jpa.repository.JpaRepository;

// 평가시즌 리포지토리
public interface SeasonRepository extends JpaRepository<Season, Long>, SeasonRepositoryCustom {
}
