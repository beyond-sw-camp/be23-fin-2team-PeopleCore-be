package com.peoplecore.salarycontract.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "salary_contract_detail")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SalaryContractDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "detail_id")
    private Long detailId;

    //    계약서의 급여 항목
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private SalaryContract contract;

    //    급여 항목 id(pay_items테이블 참조)
    @Column(name = "pay_item_id", nullable = false)
    private Long payItemId;

    //    해당 항목 금액
    @Column(name = "amount", nullable = false)
    private Integer amount;


    public void assignContract(SalaryContract contract) {
        this.contract = contract;
    }
}