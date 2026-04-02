package com.peoplecore.pay.domain;

import com.peoplecore.company.entity.Company;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Table(name = "pay_items")
public class PayItems {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long payItemId;

    @Column(length = 100, nullable = false)
    private String payItemName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayItemType payItemType;

    @Builder.Default
    private Boolean isTaxable = true;
    @Builder.Default
    private Boolean isFixed = true;
    private Integer sortOrder;
    @Builder.Default
    private Boolean isActive = true;

    @Enumerated(EnumType.STRING)
    private PayItemCategory payItemCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", foreignKey = @ForeignKey(ConstraintMode.CONSTRAINT), nullable = false)
    private Company company;

    private Boolean isLegal;

    @Enumerated(EnumType.STRING)
    private LegalCalcType legalCalcType;

}
