package com.peoplecore.salarycontract.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class SalaryContractDetailResDto {
    private  Long id;
    private Long empId;
    private String empNum;
    private String empName;
    private List<FieldDetail> fields; //폼설정+저장된 값 key=form, value=create
    private String fileName; //MinIO 객체 키 (디버그용)
    private String originalFileName; //화면에 표시할 원본 파일명
    private LocalDate registeredDate; //등록일

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class FieldDetail {
        private String fieldKey; // 필드식별자 ex. annualSalary
        private String label; //화면 표시명 ex.계약연봉
        private String section; //소속섹션 ex.급여
        private String fieldType; //입력방식 ex.NUMBER
        private String value; //저장된 값 ex."4800000"

    }


}
