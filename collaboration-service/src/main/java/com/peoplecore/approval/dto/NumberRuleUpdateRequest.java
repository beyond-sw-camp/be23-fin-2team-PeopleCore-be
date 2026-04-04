package com.peoplecore.approval.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NumberRuleUpdateRequest {
    private String numberRuleSlot1Type;     // 1번째 자리 (부서코드 등)
    private String numberRuleSlot1Custom;   // 1번째 직접입력값
    private String numberRuleSlot2Type;     // 2번째 자리 (양식코드 등)
    private String numberRuleSlot2Custom;   // 2번째 직접입력값
    private String numberRuleSlot3Type;     // 3번째 자리 (양식코드 등)
    private String numberRuleSlot3Custom;   // 3번째 직접입력값
    private String numberRuleDateFormat;    // 날짜 형식 (yyMMdd 등)
    private Integer numberRuleSeqDigits;    // 일련번호 자릿수 (3 → 001)
    private String numberRuleSeparator;     // 구분자 ("-")
    private String numberRuleSeqResetCycle; // 초기화 주기 (YEAR/MONTH/NEVER)
}
