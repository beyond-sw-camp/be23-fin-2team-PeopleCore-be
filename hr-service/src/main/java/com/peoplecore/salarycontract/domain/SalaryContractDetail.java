package com.peoplecore.salarycontract.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "연봉계약 상세")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SalaryContractDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "detail_id")
    private Long detailId;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "pay_item_id", nullable = false)
    private Long payItemId;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Column(name = "details", length = 255)
    private String details;
}
