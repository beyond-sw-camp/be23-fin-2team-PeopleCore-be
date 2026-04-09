package com.peoplecore.vacation.repository;

import com.peoplecore.vacation.entity.VacationCreateRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VacationCreateRuleRepository extends JpaRepository<VacationCreateRule,Long> {

}
