package com.peoplecore.pay.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
public class PayItems {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long payItemsId;
    @Column(length = 100, nullable = false)
    private String payItemName;
    @Enumerated(EnumType.STRING)
    private PayItemType payItemType;
    private Boolean isTaxable=true;
    private Boolean isFixed=true;
    private int sortOrder;
    private Boolean isActive=true;
    @Enumerated(EnumType.STRING)
    private PayItemCategory payItemCategory;

//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "company_id", foreignKey = @ForeignKey(ConstraintMode.CONSTRAINT), nullable = false)
//    private Company company;


}
