package com.peoplecore.pay.domain;

import com.peoplecore.entity.BaseTimeEntity;
import com.peoplecore.pay.enums.PayMonth;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "company_pay_settings")   //회사급여설정
public class CompanyPaySettings extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long companyPaySettingsId;

    private Integer salaryPayDay;
//    말일여부
    private Boolean salaryPayLastDay;

//    지급월 구분(당월,익월)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PayMonth salaryPayMonth = PayMonth.NEXT;

    @Column(nullable = false)
    private UUID companyId;

    @Column(length = 10)
    private String mainBankCode;

}
