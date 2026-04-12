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

    @Column(name = "department", length = 30)
    private String department; // 부서 (COMMON/영업팀 등)

    @Column(name = "category", length = 30)
    private String category; // 카테고리 (업무성과/역량개발/조직기여)

    @Column(name = "name", length = 100)
    private String name; // 지표명

    @Column(name = "description", length = 300)
    private String description; // 설명

    @Enumerated(EnumType.STRING)
    @Column(name = "direction")
    private KpiDirection direction; // 방향성 (UP/DOWN/MAINTAIN)

    @Column(name = "unit", length = 20)
    private String unit; // 단위 (PERCENT/COUNT/WON/HOUR/SCORE/DAY)

    @Column(name = "baseline", precision = 12, scale = 2)
    private BigDecimal baseline; // 사내평균(기준값)
}
