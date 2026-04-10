package com.peoplecore.pay.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 예상 퇴직금 산정 요청 DTO
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SeveranceEstimateReqDto {

    @NotNull(message = "예상 퇴사일은 필수입니다.")
    private LocalDate resignDate;
}
