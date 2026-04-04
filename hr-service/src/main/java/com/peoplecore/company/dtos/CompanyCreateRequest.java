package com.peoplecore.company.dtos;

import com.peoplecore.company.domain.ContractType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

// 회사 등록 요청
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyCreateRequest {

    @NotBlank
    private String companyName;

    private LocalDate foundedAt;
    private String companyIp;

    @NotNull
    private LocalDate contractStartAt;

    @NotNull
    private LocalDate contractEndAt;

    @NotNull
    private ContractType contractType;

    @NotNull
    @Min(1)
    private Integer maxEmployees;

//    email설정 다시
//    최고관리자 계정 메일 수신자
    @NotBlank
    private String adminEmail;

    @NotBlank
    private String adminName;

}
