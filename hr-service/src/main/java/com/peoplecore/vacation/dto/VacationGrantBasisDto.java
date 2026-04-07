package com.peoplecore.vacation.dto;


import com.peoplecore.vacation.entity.VacationPolicy;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VacationGrantBasisDto {
    @NotNull
    private String grantBasis;

    public static VacationGrantBasisDto from(VacationPolicy policy) {
        return VacationGrantBasisDto.builder()
                .grantBasis(policy.getPolicyBaseType().name())
                .build();
    }
}
