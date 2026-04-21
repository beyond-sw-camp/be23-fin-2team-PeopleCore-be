package com.peoplecore.evaluation.domain;

import com.peoplecore.company.domain.Company;
import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

// KPI 옵션 - 카테고리/단위/부서depth 드롭다운 선택지 (회사별, type 으로 구분)
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
    private Long optionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private KpiOptionType type;

    // 옵션 값
    //   - CATEGORY/UNIT : 표시명이자 저장값 ("업무성과", "%")
    //   - DEPARTMENT    : depth 설정값 ("1".."N" 또는 "leaf")
    @Column(name = "option_value", nullable = false, length = 50)
    private String optionValue;

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // 소프트 삭제 — KpiTemplate FK 보호용 (물리 삭제 X)
    public void deactivate() {
        this.isActive = false;
    }

    // 순서 재배치
    public void updateSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    // 값 갱신 — rename(CATEGORY/UNIT) / depth 변경(DEPARTMENT) 공용
    public void updateValue(String newValue) {
        this.optionValue = newValue;
    }
}
