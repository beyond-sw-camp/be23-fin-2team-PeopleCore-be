package com.peoplecore.pay.dtos;

import com.peoplecore.pay.domain.InsuranceRates;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InsuranceRatesResDto {

    private Long insuranceRatesId;
    private Integer year;

//    국민연금(근로자,사업주 동일)
    private BigDecimal nationalPension;
//    건강보험(근로자,사업주 동일)
    private BigDecimal healthInsurance;
//    장기요양보험(건강보험의 %)
    private BigDecimal longTermCare;
//    고용보험(근로자)
    private BigDecimal employInsurance;
    //    고용보험(사업주)
    private BigDecimal employInsuranceEmployer;
//    국민연금 상한/하한
    private Long pensionUpperLimit;
    private Long pensionLowerLimit;

    private LocalDate validFrom;
    private LocalDate validTo;
    private LocalDateTime updatedAt;



    public static InsuranceRatesResDto fromEntity(InsuranceRates r) {
        return InsuranceRatesResDto.builder()
                .insuranceRatesId(r.getInsuranceRatesId())
                .year(r.getYear())
                .nationalPension(r.getNationalPension())
                .healthInsurance(r.getHealthInsurance())
                .longTermCare(r.getLongTermCare())
                .employInsurance(r.getEmploymentInsurance())
                .employInsuranceEmployer(r.getEmploymentInsuranceEmployer())
                .pensionUpperLimit(r.getPensionUpperLimit())
                .pensionLowerLimit(r.getPensionLowerLimit())
                .validFrom(r.getValidFrom())
                .validTo(r.getValidTo())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
