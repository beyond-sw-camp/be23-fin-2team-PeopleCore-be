package com.peoplecore.company.dtos;

import com.peoplecore.company.entity.CompanyStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;


// 회사 상태 변경 요청
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyStatusUpdateRequest {

    @NotNull
    private CompanyStatus status;
    private String reason;

}
