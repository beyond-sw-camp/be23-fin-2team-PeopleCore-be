package com.peoplecore.dto;

import com.peoplecore.enums.ContractType;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 회사 등록 요청 DTO
 * POST /internal/companies
 *
 * 기본정보 등록 + 최고관리자 계정 생성을 한번에 처리
 */
@Getter
@NoArgsConstructor
public class CompanyCreateRequest {

    // ── 회사 기본정보 ──
    @NotBlank(message = "회사명은 필수입니다")
    @Size(max = 100)
    private String companyName;

    private LocalDate foundingDate;

    @Size(max = 45)
    private String ipAddress;

    // ── 계약 정보 ──
    @NotNull(message = "계약 시작일은 필수입니다")
    private LocalDate contractStartDate;

    @NotNull(message = "계약 종료일은 필수입니다")
    private LocalDate contractEndDate;

    @NotNull(message = "계약 유형은 필수입니다")
    private ContractType contractType;

    @NotNull(message = "최대 사원 수는 필수입니다")
    @Min(value = 1, message = "최대 사원 수는 1명 이상이어야 합니다")
    private Integer maxEmployees;

    // ── 담당자 정보 ──
    private String contactName;
    private String contactEmail;
    private String contactPhone;

    // ── 최고관리자 계정 정보 ──
    @NotBlank(message = "관리자 이름은 필수입니다")
    private String adminName;

    @NotBlank(message = "관리자 이메일은 필수입니다")
    @Email(message = "올바른 이메일 형식이 아닙니다")
    private String adminEmail;
}
