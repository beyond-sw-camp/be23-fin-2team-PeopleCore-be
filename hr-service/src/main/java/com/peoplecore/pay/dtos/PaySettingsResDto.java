package com.peoplecore.pay.dtos;

import com.peoplecore.pay.domain.CompanyPaySettings;
import com.peoplecore.pay.enums.PayMonth;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaySettingsResDto {

    private Long companyPaySettingsId;
    private Integer salaryPayDay;
    private Boolean salaryPayLastDay;
    private PayMonth salaryPayMonth;    //NEXT, CURRENT
    private String salaryPayMonthLabel; //익월, 당월
    private String mainBankCode;
    private String mainBankName;
    private LocalDateTime updatedAt;

    public static PaySettingsResDto fromEntity(CompanyPaySettings s){
        return PaySettingsResDto.builder()
                .companyPaySettingsId(s.getCompanyPaySettingsId())
                .salaryPayDay(s.getSalaryPayDay())
                .salaryPayLastDay(s.getSalaryPayLastDay())
                .salaryPayMonth(s.getSalaryPayMonth())
                .salaryPayMonthLabel(s.getSalaryPayMonth() == PayMonth.NEXT ? "익월" : "당월")
                .mainBankCode(s.getMainBankCode())
                .mainBankName(s.getMainBankName())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}
