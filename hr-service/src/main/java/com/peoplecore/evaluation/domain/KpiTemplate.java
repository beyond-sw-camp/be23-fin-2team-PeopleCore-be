package com.peoplecore.evaluation.domain;

import com.peoplecore.department.domain.Department;
import com.peoplecore.entity.BaseTimeEntity;
import com.peoplecore.grade.domain.Grade;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

// KPI지표 - 부서/카테고리별 KPI 템플릿
@Entity
@Table(name = "kpi_template")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KpiTemplate extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "kpi_id")
    private Long kpiId; // KPI PK

//    실제 부서는 조직도(Department) 직접 참조
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    // 직급 — null 이면 해당 부서 전 직급 공통 KPI
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_id")
    private Grade grade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_option_id", nullable = false)
    private KpiOption category; // 카테고리 옵션 (KpiOption CATEGORY)

    @Column(name = "name", length = 100, nullable = false)
    private String name; // 지표명

    @Column(name = "description", length = 300, nullable = false)
    private String description; // 설명


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_option_id", nullable = false)
    private KpiOption unit; // 단위 옵션 (KpiOption UNIT)

    @Column(name = "baseline", precision = 12, scale = 2)
    private BigDecimal baseline; // 사내평균(기준값)

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", length = 10, nullable = false)
    @Builder.Default
    private KpiDirection direction = KpiDirection.UP; // 지표 방향성 (UP/DOWN/MAINTAIN)

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // 소프트 삭제 - 과거 Goal 의 KPI 참조 보호용
    public void deactivate() {
        this.isActive = false;
    }

    // KPI 지표 수정 - 등록/수정 폼에서 받는 필드 일괄 갱신 (grade 는 nullable)
    public void update(Department department,
                       Grade grade,
                       KpiOption category,
                       KpiOption unit,
                       String name,
                       String description,
                       KpiDirection direction) {
        this.department = department;
        this.grade = grade;
        this.category = category;
        this.unit = unit;
        this.name = name;
        this.description = description;
        this.direction = direction;
    }
}
