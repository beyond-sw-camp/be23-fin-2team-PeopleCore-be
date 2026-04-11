package com.peoplecore.pay.domain;

import com.peoplecore.company.domain.Company;
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
@Table(name = "payroll_details") //급여산정상세
public class PayrollDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long payrollDetailsId;

    @Column(nullable = false)
    private Long payrollRunId;

    @Column(nullable = false)
    private Long empId;

    @Column(nullable = false)
    private Long payItemId;

//    항목별금액
    @Column(nullable = false)
    private Long amount;

    private String memo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(length = 100)   //스냅샷용 항목명
    private String payItemName;

}
