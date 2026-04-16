package com.peoplecore.evaluation.domain;

import com.peoplecore.entity.BaseTimeEntity;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_option_id")
    private KpiOption department; // 부서 옵션 (KpiOption DEPARTMENT)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_option_id")
    private KpiOption category; // 카테고리 옵션 (KpiOption CATEGORY)

    @Column(name = "name", length = 100)
    private String name; // 지표명

    @Column(name = "description", length = 300)
    private String description; // 설명

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "direction_option_id")
    private KpiOption direction; // 방향성 옵션 (KpiOption DIRECTION)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_option_id")
    private KpiOption unit; // 단위 옵션 (KpiOption UNIT)

    @Column(name = "baseline", precision = 12, scale = 2)
    private BigDecimal baseline; // 사내평균(기준값)
}
