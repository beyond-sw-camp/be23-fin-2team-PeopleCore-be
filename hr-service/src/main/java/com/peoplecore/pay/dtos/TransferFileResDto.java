package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransferFileResDto {

    private String fileName;
    private byte[] fileBytes;
    private List<String> skippedEmployees;   // 급여대장 누락 사유 alert - 예: ["오수빈(계좌 미등록)", "황민재(은행코드 누락)"]
}
