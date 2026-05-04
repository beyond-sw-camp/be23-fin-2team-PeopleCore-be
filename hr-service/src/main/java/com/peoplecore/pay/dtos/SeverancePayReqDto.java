package com.peoplecore.pay.dtos;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeverancePayReqDto {
//    퇴직금 지급처리 요청

    @NotEmpty(message = "지급처리할 sevIds는 비어있을 수 없습니다")
    private List<Long> sevIds;

    @NotNull(message = "이체일은 필수입니다")
    private LocalDate transferDate;     // 이체예정일
}
