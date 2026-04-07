package com.peoplecore.vacation.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 연차 발생 규칙
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationCreateRule extends BaseTimeEntity {

    /**
     * 연차 발생 규칙 id
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long createRuleId;

    /**
     * 연차 정책 Id
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private VacationPolicy vacationPolicy;

    /**
     * 근속 연수 이상
     */
    @Column(nullable = false)
    private Integer createRuleMinYear;

    /**
     * 근속 연수 미만
     */
    private Integer createRuleMaxYear;

    /**
     * 발생 연차 일수
     */
    @Column(nullable = false)
    private Integer createRuleDay;

    /*비고란 */
    private String createRuleDesc;

    /**
     * 규착 생성자 id
     */
    @Column(nullable = false)
    private Long createRuleEmpId;


    /*규칙 생성 팩토리 메서드 */
    public static VacationCreateRule create(VacationPolicy policy, Integer minYear, Integer maxYear, Integer day, String desc, Long empId) {
        return VacationCreateRule.builder()
                .vacationPolicy(policy)
                .createRuleMinYear(minYear)
                .createRuleMaxYear(maxYear)
                .createRuleDay(day)
                .createRuleEmpId(empId)
                .createRuleDesc(desc)
                .build();
    }

    /* 규칙 수정 */
    public void update(Integer minYear, Integer maxYear, Integer day, String desc) {
        this.createRuleMinYear = minYear;
        this.createRuleMaxYear = maxYear;
        this.createRuleDay = day;
        this.createRuleDesc = desc;

    }
}
