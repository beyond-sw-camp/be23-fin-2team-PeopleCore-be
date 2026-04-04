package com.peoplecore.company.dtos;

import com.peoplecore.company.domain.ContractType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CompanyCreateReqDto {

    @NotBlank(message = "회사명은 필수입니다")
    private String companyName;

    private LocalDate foundedAt;

    private String companyIp;

    @NotNull(message = "계약 시작일은 필수입니다")
    private LocalDate contractStartAt;

    @NotNull(message = "계약 종료일은 필수입니다")
    private LocalDate contractEndAt;

    @NotNull(message = "계약 유형은 필수입니다")
    private ContractType contractType;

    @NotNull(message = "최대 사원 수는 필수입니다")
    @Min(value = 1)
    private Integer maxEmployees;

    // 최고관리자 계정 정보
    @NotBlank(message = "관리자 이름은 필수입니다")
    private String adminName;

    @NotBlank(message = "관리자 이메일은 필수입니다")
    @Email
    private String adminEmail;
}
}
