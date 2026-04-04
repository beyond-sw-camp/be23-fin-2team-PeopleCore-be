package com.peoplecore.dto;

import com.peoplecore.enums.CompanyStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 계약 상태 변경 요청 DTO
 * PATCH /internal/companies/{companyId}/status
 */
@Getter
@NoArgsConstructor
public class CompanyStatusUpdateRequest {

    @NotNull(message = "변경할 상태는 필수입니다")
    private CompanyStatus status;

    private String reason; // 상태 변경 사유 (선택)
}
