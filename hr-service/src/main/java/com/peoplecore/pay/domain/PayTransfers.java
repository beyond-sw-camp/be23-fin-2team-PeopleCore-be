package com.peoplecore.pay.domain;

import com.peoplecore.company.domain.Company;
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
@Table(name = "pay_transfers")  //급여지급
public class PayTransfers {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long payTransfersId;

    @Column(nullable = false)
    private Long payrollRunId;

    @Column(nullable = false)
    private Long empId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

}
