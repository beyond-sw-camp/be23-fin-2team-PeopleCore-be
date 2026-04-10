package com.peoplecore.vacation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationRuleCreateRequest {

    @NotNull
    private Integer minYears;

    /* null이면 */
    private Integer maxYears;

    @NotNull
    private Integer days;

    private String desc;
}
