package com.peoplecore.vacation.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/* 연차 발생 규칙 - 근속 연수 구간별 발생 일수. VacationPolicy 의 자식 (cascade ALL) */
@Entity
@Table(
        name = "vacation_grant_rule",
        indexes = {
                @Index(name = "idx_vacation_grant_rule_policy",
                        columnList = "vacation_policy_id, min_year")
        }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationGrantRule extends BaseTimeEntity {

    /* 규칙 ID (PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rule_id")
    private Long ruleId;

    /* 소속 연차 정책 - LAZY 로딩 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vacation_policy_id", nullable = false)
    private VacationPolicy vacationPolicy;

    /* 근속 연수 이상 (포함) - 1, 3, 5, ... */
    @Column(name = "min_year", nullable = false)
    private Integer minYear;

    /* 근속 연수 미만 (미포함) - NULL 이면 상한 없음 (예: 21년 이상) */
    @Column(name = "max_year")
    private Integer maxYear;

    /* 발생 연차 일수 */
    @Column(name = "grant_days", nullable = false)
    private Integer grantDays;

    /* 비고 */
    @Column(name = "description")
    private String description;

    /* 규칙 생성/수정자 사원 ID. 시스템 자동 생성 시 0L */
    @Column(name = "created_emp_id", nullable = false)
    private Long createdEmpId;


    /* 단일 규칙 생성 - 정책에 부착하는 팩토리 */
    public static VacationGrantRule create(VacationPolicy policy,
                                           Integer minYear, Integer maxYear,
                                           Integer grantDays, String description, Long empId) {
        return VacationGrantRule.builder()
                .vacationPolicy(policy)
                .minYear(minYear)
                .maxYear(maxYear)
                .grantDays(grantDays)
                .description(description)
                .createdEmpId(empId)
                .build();
    }

    /* 회사 생성 시 기본 규칙 11건 - 근로기준법 표준 (1년차 15일, 2년마다 +1, 25일 상한) */
    /* 1~3년=15 / 3~5년=16 / 5~7년=17 / ... / 19~21년=24 / 21년 이상=25 */
    public static List<VacationGrantRule> createCompanyDefaults(VacationPolicy policy, Long creatorEmpId) {
        List<VacationGrantRule> rules = new ArrayList<>();
        int day = 15;
        int year = 1;
        /* 1~21년 미만 구간: 2년 단위로 +1일 */
        while (day < 25) {
            rules.add(create(policy, year, year + 2, day, "기본 규칙(근로기준법 표준)", creatorEmpId));
            year += 2;
            day += 1;
        }
        /* 21년 이상: 상한 없음(maxYear=null), 25일 고정 */
        rules.add(create(policy, year, null, 25, "기본 규칙(근로기준법 표준)", creatorEmpId));
        return rules;
    }

    /* 규칙 수정 */
    public void update(Integer minYear, Integer maxYear, Integer grantDays, String description) {
        this.minYear = minYear;
        this.maxYear = maxYear;
        this.grantDays = grantDays;
        this.description = description;
    }

    /* 근속 연수 매칭 - 스케줄러가 사원 근속에 맞는 규칙 찾을 때 사용 */
    public boolean matches(int yearsOfService) {
        if (yearsOfService < minYear) return false;
        return maxYear == null || yearsOfService < maxYear;
    }
}