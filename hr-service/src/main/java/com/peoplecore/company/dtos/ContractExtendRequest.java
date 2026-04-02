package com.peoplecore.company.dtos;

import com.peoplecore.company.entity.ContractType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// 계약 연장 요청
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractExtendRequest {

    @NotNull
    private LocalDate newContractEndAt;

    private ContractType contractType;
    private Integer maxEmployees;

//    email설정 다시
//    알람 초기화 후 재발송 수신자
    @NotBlank
    private String adminEmail;

}
