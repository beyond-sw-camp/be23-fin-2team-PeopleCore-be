package com.peoplecore.salarycontract.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class SalaryContractCreateReqDto {
    @NotNull(message = "없는 사원입니다")
    private Long empId;
    @NotEmpty(message = "필드값이 비어있습니다")
    private List<FieldValue> fields;

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class FieldValue {

        @NotBlank(message = "필드키는 필수입니다")
        private String fieldKey; //필드 식별자(ex.contractYear,annualSalary)

        private String value; //입력값

//    ex. "empId": 1,
//        "fields": [
//        { "fieldKey": "contractYear",  "value": "2026" },
//        { "fieldKey": "contractStart", "value": "2026-01-01" }
//        ]

    }
}
