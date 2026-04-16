package com.peoplecore.pay.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeveranceCalcReqDto {

    @NotNull(message = "사원 ID는 필수입니다")
    private Long empId;
}
