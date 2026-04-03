package com.peoplecore.pay.domain;

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
@Table(name = "pay_stubs")
public class PayStubs {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long payStubsId;

    @Column(nullable = false)
    private String payYearMonth;

    private Long totalPay;
    private Long totalDeduction;
    private Long netPay;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SendStatus sendStatus;

    private LocalDateTime issuedAt;

    @Column(nullable = false)
    private UUID companyId;

}
