package com.peoplecore.evaluation.dto;


import com.peoplecore.evaluation.domain.SeasonPeriod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class SeasonUpdateRequestDto {

    @NotBlank(message = "시즌명은 필수입니다")
    private String name;

    private SeasonPeriod period;

    @NotNull(message = "시작일은 필수입니다")
    private LocalDate startDate;

    @NotNull(message = "종료일은 필수입니다")
    private LocalDate endDate;
}

