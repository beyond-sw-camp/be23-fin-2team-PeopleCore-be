package com.peoplecore.dto;

import com.peoplecore.enums.ContractType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 계약 연장 요청 DTO
 * PATCH /internal/companies/{companyId}/contract/extend
 *
 * 구두/메일 계약 확정 후 연장 처리
 * - 만료일 재설정
 * - max_employees, 계약유형 변경 가능
 * - EXPIRED → ACTIVE 자동 복구
 * - 알림 초기화
 */
@Getter
@NoArgsConstructor
public class ContractExtendRequest {

    @NotNull(message = "새 만료일은 필수입니다")
    @Future(message = "만료일은 미래 날짜여야 합니다")
    private LocalDate newContractEndDate;

    @Min(value = 1, message = "최대 사원 수는 1명 이상이어야 합니다")
    private Integer maxEmployees;    // null이면 기존 값 유지

    private ContractType contractType; // null이면 기존 값 유지
}
