package com.peoplecore.pay.domain;

import com.peoplecore.company.domain.Company;
import com.peoplecore.pay.enums.PensionType;
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
@Table(name = "retirement_settings",   //퇴직연금설정
    indexes = {
        @Index(name = "idx_retirement_company", columnList = "company_id")
    })

public class RetirementSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long retirementSettingsId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PensionType pensionType;

//    퇴직연금 운용사(DB형)
    @Column(length = 100)
    private String pensionProvider;

//    퇴직연금계좌번호(DB형)
    @Column(length = 100)
    private String pensionAccount;


    public void update(PensionType pensionType, String pensionProvider, String pensionAccount){
        this.pensionType = pensionType;

        if(pensionType == PensionType.DB || pensionType == PensionType.DB_DC){
            this.pensionProvider = pensionProvider;
            this.pensionAccount = pensionAccount;
        } else {
            this.pensionProvider = null;
            this.pensionAccount = null;
        }

    }

}
