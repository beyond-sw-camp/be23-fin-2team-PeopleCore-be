package com.peoplecore.pay.domain;

import com.peoplecore.pay.enums.PensionType;
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
@Table(name = "retirement_settings")   //퇴직연금설정
public class RetirementSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long retirementSettingsId;

    @Column(nullable = false)
    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PensionType pensionType;

//    퇴직연금 운용사(DB형)
    @Column(length = 100)
    private String pensionProvider;

//    퇴직연금계좌번호(DB형)
    @Column(length = 100)
    private String pensionAccount;
}
