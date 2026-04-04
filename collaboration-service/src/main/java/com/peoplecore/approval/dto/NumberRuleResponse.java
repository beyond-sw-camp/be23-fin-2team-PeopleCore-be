package com.peoplecore.approval.dto;

import com.peoplecore.approval.entity.ApprovalNumberRule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NumberRuleResponse {
    private Long numberRuleId;
    private String numberRuleSlot1Type;
    private String numberRuleSlot1Custom;
    private String numberRuleSlot2Type;
    private String numberRuleSlot2Custom;
    private String numberRuleSlot3Type;
    private String numberRuleSlot3Custom;
    private String numberRuleDateFormat;
    private Integer numberRuleSeqDigits;
    private String numberRuleSeparator;
    private String numberRuleSeqResetCycle;
    private String preview;  /*미리보기 : 부서-유형-날짜-문서번호*/

    public static NumberRuleResponse from(ApprovalNumberRule rule, String preview) {
        return NumberRuleResponse.builder()
                .numberRuleId(rule.getNumberRuleId())
                .numberRuleSlot1Type(rule.getNumberRuleSlot1Type())
                .numberRuleSlot1Custom(rule.getNumberRuleSlot1Custom())
                .numberRuleSlot2Type(rule.getNumberRuleSlot2Type())
                .numberRuleSlot2Custom(rule.getNumberRuleSlot2Custom())
                .numberRuleSlot3Type(rule.getNumberRuleSlot3Type())
                .numberRuleSlot3Custom(rule.getNumberRuleSlot3Custom())
                .numberRuleDateFormat(rule.getNumberRuleDateFormat())
                .numberRuleSeqDigits(rule.getNumberRuleSeqDigits())
                .numberRuleSeparator(rule.getNumberRuleSeparator())
                .numberRuleSeqResetCycle(rule.getNumberRuleSeqResetCycle().name())
                .preview(preview)
                .build();
    }
}
