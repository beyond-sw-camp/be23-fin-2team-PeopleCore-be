package com.peoplecore.pay.dtos;

import com.peoplecore.pay.enums.DepStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PensionDepositCreateReqDto {
// 퇴직연금 생성요청

    @NotNull(message = "사원 ID는 필수입니다")
    private Long empId;

    @NotBlank(message = "적립 기준월은 필수입니다")
    @Pattern(regexp = "\\d{4}-\\d{2}", message = "YYYY-MM 형식이어야 합니다")
    private String payYearMonth;

    @NotNull @Positive(message = "기준임금은 0보다 커야 합니다")
    private Long baseAmount;

    @NotNull @Positive(message = "적립금액은 0보다 커야 합니다")
    private Long depositAmount;

    @NotNull(message = "상태는 필수입니다")
    private DepStatus depStatus;

    @NotBlank(message = "사유는 필수입니다")
    private String reason;
}
