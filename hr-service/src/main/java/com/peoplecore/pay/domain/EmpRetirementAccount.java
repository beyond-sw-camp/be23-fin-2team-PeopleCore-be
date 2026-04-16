package com.peoplecore.pay.domain;

import com.peoplecore.company.domain.Company;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.entity.BaseTimeEntity;
import com.peoplecore.pay.enums.RetirementType;
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
@Table(name = "emp_retirement_account")   //사원 퇴직연금계좌
public class EmpRetirementAccount extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long retirementAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RetirementType retirementType;

    @Column(nullable = false, length = 100)
    private String pensionProvider;

    @Column(length = 50, nullable = false)
    private String accountNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="emp_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;


    public void update(RetirementType retirementType, String pensionProvider, String accountNumber){
        this.retirementType = retirementType;
        this.pensionProvider = pensionProvider;
        this.accountNumber = accountNumber;
    }
}
