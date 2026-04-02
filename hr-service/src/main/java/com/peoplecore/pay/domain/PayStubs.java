package com.peoplecore.pay.domain;

import com.peoplecore.pay.enums.SendStatus;
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
@Table(name = "pay_stubs")  //급여명세
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

    private LocalDateTime sentAt;
    @Column(length = 500)
    private String pdfUrl;
    private LocalDateTime issuedAT;

    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private Long payrollRunId;

    @Column(nullable = false)
    private Long empId;


}
