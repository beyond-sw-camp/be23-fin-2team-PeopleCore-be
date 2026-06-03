package com.peoplecore.calendar.repository;

import com.peoplecore.calendar.entity.RepeatedRules;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RepeatedRulesRepository extends JpaRepository<RepeatedRules, Long> {

//    events 삭제후, 어떤 events도 참조하지 않는 규칙만 제거
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM RepeatedRules r WHERE r.repeatedRulesId IN :ruleIds " +
            "AND NOT EXISTS (SELECT 1 from Events e WHERE e.repeatedRules.repeatedRulesId = r.repeatedRulesId)")
    void deleteOrphansByIds(@Param("ruleIds")List<Long> ruleIds);
}
