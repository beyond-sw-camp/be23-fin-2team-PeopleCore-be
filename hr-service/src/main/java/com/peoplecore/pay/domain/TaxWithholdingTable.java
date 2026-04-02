package com.peoplecore.pay.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "tax_withholding_table")   //간이세액표
public class TaxWithholdingTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long taxId;

    @Column(nullable = false)
    private Integer taxYear;

    @Column(nullable = false)
    private Long salaryFrom;

    @Column(nullable = false)
    private Long salaryTo;

//    부양가족수
    @Column(nullable = false)
    private Integer dependents;

//    지방소득세 : 소득세*10%
    @Column(nullable = false)
    private Long incomeTax;


}
