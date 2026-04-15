package com.peoplecore.pay.domain;

import com.peoplecore.company.domain.Company;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.entity.BaseTimeEntity;
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
@Table(name = "emp_accounts")   //사원계좌
public class EmpAccounts extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long empAccountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="emp_id", nullable = false)
    private Employee employee;

    @Column(length = 50, nullable = false)
    private String bankName;

    @Column(length = 50, nullable = false)
    private String accountNumber;

    @Column(length = 10)
    private String bankCode;    // 은행코드 3자리 (예: "088")

//    예금주
    @Column(length = 50, nullable = false)
    private String accountHolder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;


    public void update(String bankName, String accountNumber, String accountHolder, String bankCode){
        this.bankName = bankName;
        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
        this.bankCode = bankCode;
    }
}
