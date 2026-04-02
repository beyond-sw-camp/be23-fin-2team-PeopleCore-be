package com.peoplecore.pay.domain;

import com.peoplecore.company.entity.Company;
import com.peoplecore.pay.enums.LegalCalcType;
import com.peoplecore.pay.enums.PayItemCategory;
import com.peoplecore.pay.enums.PayItemType;
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
@Table(name = "pay_items")  //급여항목
public class PayItems {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long payItemId;

    @Column(length = 100, nullable = false)
    private String payItemName;

//    지급/공제 구분
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayItemType payItemType;

    @Builder.Default
    private Boolean isTaxable = true;

//    고정항목여부
    @Builder.Default
    private Boolean isFixed = true;

    private Integer sortOrder;

    @Builder.Default
    private Boolean isActive = true;

    @Enumerated(EnumType.STRING)
    private PayItemCategory payItemCategory;

    @Column(nullable = false)
    private UUID companyId;

//    법정수당여부
    private Boolean isLegal;

//    법정산정방식구분용
    @Enumerated(EnumType.STRING)
    private LegalCalcType legalCalcType;

}
