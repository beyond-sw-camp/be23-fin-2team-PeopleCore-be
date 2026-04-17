package com.peoplecore.vacation.repository;

import com.peoplecore.vacation.entity.VacationGrantRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/* 연차 발생 규칙 레포 (VacationPolicy 의 자식, cascade ALL) */
@Repository
public interface VacationGrantRuleRepository extends JpaRepository<VacationGrantRule, Long> {

    /*
     * 정책 ID 로 규칙 정렬 목록
     * 용도: 관리자 화면 (연차 발생 규칙 표 표시), 정책 fetch join 안 한 경우
     * 정렬: minYear 오름차순 (1년차부터 순서대로)
     * 인덱스: idx_vacation_grant_rule_policy (커버)
     */
    List<VacationGrantRule> findAllByVacationPolicy_PolicyIdOrderByMinYearAsc(Long policyId);

    /*
     * 근속 연수 매칭 규칙 단건 조회
     * 용도: 연차 발생 잡 - 사원 근속 N년에 해당하는 규칙 1건 찾기
     * 조건: minYear <= years AND (maxYear IS NULL OR years < maxYear)
     * 반환: Optional - 회사가 규칙 누락한 경우 empty (예외 처리)
     */
    @Query("""
            SELECT r FROM VacationGrantRule r
             WHERE r.vacationPolicy.policyId = :policyId
               AND r.minYear <= :years
               AND (r.maxYear IS NULL OR :years < r.maxYear)
            """)
    Optional<VacationGrantRule> findMatchingRule(@Param("policyId") Long policyId,
                                                 @Param("years") int yearsOfService);

    /*
     * 정책 내 근속 구간 중첩 검사용 - 기존 규칙 전체 조회 (excludeRuleId 제외)
     * 용도: 규칙 생성/수정 시 (minYear, maxYear) 범위 중첩 방지
     * excludeRuleId null 이면 자기 자신 제외 없음 (create 용)
     */
    @Query("""
            SELECT r FROM VacationGrantRule r
             WHERE r.vacationPolicy.policyId = :policyId
               AND (:excludeRuleId IS NULL OR r.ruleId <> :excludeRuleId)
            """)
    List<VacationGrantRule> findAllForOverlapCheck(@Param("policyId") Long policyId,
                                                   @Param("excludeRuleId") Long excludeRuleId);
}