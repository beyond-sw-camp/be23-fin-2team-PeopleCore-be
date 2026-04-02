package com.peoplecore.pay.domain;

import com.peoplecore.company.entity.Company;
import com.peoplecore.pay.enums.SevStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "severance_pays")   //DB형 퇴직금
public class SeverancePays {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sevId;

    @Column(nullable = false)
    private Long empId;

    @Column(nullable = false)
    private LocalDate resignDate;

//    근속연수
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal serviceYears;

    @Column(nullable = false)
    private Long avg3monthPay;
//    퇴직금산축액
    @Column(nullable = false)
    private Long severanceAmount;
//    퇴직소득세
    @Column(nullable = false)
    private Long taxAmount;
//    실지급액
    @Column(nullable = false)
    private Long netAmount;
//    지급일
    @Column(nullable = false)
    private LocalDate transferDate;

//    퇴직금상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SevStatus sevStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

//   확정자
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
//    지급처리자
    private Long paidBy;
    private LocalDateTime paidAt;

}
