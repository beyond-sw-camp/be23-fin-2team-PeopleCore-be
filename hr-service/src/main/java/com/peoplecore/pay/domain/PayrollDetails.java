package com.peoplecore.pay.domain;

import com.peoplecore.company.domain.Company;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.pay.enums.PayItemType;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_run_id", nullable = false)
    private PayrollRuns payrollRuns;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pay_item_id", nullable = false)
    private PayItems payItems;

//    항목별금액
    private Long amount;
    private String memo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(length = 100)   //스냅샷용 항목명
    private String payItemName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayItemType payItemType;    // 스냅샷용

}
