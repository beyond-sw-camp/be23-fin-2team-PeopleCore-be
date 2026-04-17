package com.peoplecore.evaluation.domain;

import com.peoplecore.company.domain.Company;
import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

// KPI 옵션 - KpiTemplate 드롭다운 선택지 (방향/단위/카테고리/부서)
@Entity
@Table(name = "kpi_option")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KpiOption extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "option_id")
    private Long optionId; // 옵션 PK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company; // 소속 회사

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private KpiOptionType type; // 옵션 종류

    @Column(name = "code", nullable = false, length = 30)
    private String code; // 저장 코드 (예: UP, PERCENT)

    @Column(name = "label", nullable = false, length = 50)
    private String label; // 표시명 (예: 상승 지향)

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0; // 정렬 순서

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true; // 활성 여부
}
