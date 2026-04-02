package com.peoplecore.pay.domain;

import com.peoplecore.pay.enums.DepStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "retirement_pension_deposits")   //퇴직연금적립-DC형
public class RetirementPensionDeposits {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long depId;

    @Column(nullable = false)
    private Long empId;

//    적립기준임금
    @Column(nullable = false)
    private Long baseAmount;

//    적립금액 : 연간임금/12
    @Column(nullable = false)
    private Long depositAmount;

    private LocalDateTime depositDate;

//    퇴직연금 상태 (적립예정,완료)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DepStatus depStatus;

    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private Long payrollRunId;

}
