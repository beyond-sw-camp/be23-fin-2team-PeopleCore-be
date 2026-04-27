package com.peoplecore.pay.dtos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DependentsUpdateReqDto {
//    부양가족수 변경
    @NotNull
    @Min(value = 0, message = "부양가족수는 0 이상이어야 합니다.")
    @Max(value = 20, message = "부양가족수는 20 이하여야 합니다.")
    private Integer dependentsCount;
}
