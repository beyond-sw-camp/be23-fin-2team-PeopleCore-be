package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PayrollTransferDto {
    private String empName;         // 사원명 (예금주)
    private String bankCode;        // 입금은행 코드 3자리
    private String bankName;        // 입금은행명
    private String accountNumber;   // 입금계좌번호 ('-' 제거)
    private Long netPay;            // 실지급액 (이체금액)
    private String memo;            // 비고

}
