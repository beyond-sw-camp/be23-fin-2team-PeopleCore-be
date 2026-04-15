package com.peoplecore.pay.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeverancePayReqDto {
//    신규 - 지급처리 요청

    @NotNull(message = "이체일은 필수입니다")
    private LocalDate transferDate;     // 이체예정일
}
