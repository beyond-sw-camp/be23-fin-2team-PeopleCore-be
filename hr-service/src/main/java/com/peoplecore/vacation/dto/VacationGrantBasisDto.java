package com.peoplecore.vacation.dto;


import com.peoplecore.vacation.entity.VacationPolicy;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 연차 지급 기준 DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VacationGrantBasisDto {

    /** HIRE / FISCAL */
    @NotNull
    private String grantBasis;

    /**
     * 회계연도 시작일 (mm-dd)
     * - FISCAL 일 때 필수, HIRE 일 때 무시 (서버가 null 로 저장)
     */
    private String fiscalYearStart;

    public static VacationGrantBasisDto from(VacationPolicy policy) {
        return VacationGrantBasisDto.builder()
                .grantBasis(policy.getPolicyBaseType().name())
                .fiscalYearStart(policy.getPolicyFiscalYearStart())
                .build();
    }
}
