package com.peoplecore.approval.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;

import java.util.UUID;

import lombok.*;

/**
 * 결재 번호 규칙
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalNumberRule extends BaseTimeEntity {

    public enum NumberRuleSeqResetCycle {
        YEAR,
        MONTH,
        NEVER
    }

    /**
     * 결재 번호 규칙
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long numberRuleId;

    /**
     * 회사 id
     */
    @Column(nullable = false, unique = true)
    private UUID numberRuleCompanyId;

    /**
     * 1번째 자리
     */
    @Column(nullable = false)
    private String numberRuleSlot1Type;

    /**
     * 1번째 직접 입력값
     */
    private String numberRuleSlot1Custom;

    /**
     * 2번째 자리
     */
    @Column(nullable = false)
    private String numberRuleSlot2Type;

    /**
     * 2번째 직접 입력값
     */
    private String numberRuleSlot2Custom;

    /*3번째 자리*/
    @Column(nullable = false)
    private String numberRuleSlot3Type;

    /**
     * 3번째 직접 입력값
     */
    private String numberRuleSlot3Custom;

    /**
     * 날짜 형식 YYMMDD
     */
    @Column(nullable = false)
    private String numberRuleDateFormat;

    /**
     * 일련 번호 자릿수
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer numberRuleSeqDigits = 3;

    /**
     * 구분자
     */
    @Column(nullable = false, length = 1)
    @Builder.Default
    private String numberRuleSeparator = "-";

    /**
     * 일련번호 초기화 주기
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private NumberRuleSeqResetCycle numberRuleSeqResetCycle = NumberRuleSeqResetCycle.YEAR;

    /**
     * 현재 일련번호
     */
    @Column(nullable = false)
    private Integer numberRuleCurrentSeq;

    /**
     * 규칙 생성자 id]
     */
    @Column(nullable = false)
    private Long numberRuleEmpId;


    public void updateRule(String slot1Type, String slot1Custom,
                           String slot2Type, String slot2Custom,
                           String slot3Type, String slot3Custom,
                           String dateFormat, Integer seqDigits,
                           String separator, NumberRuleSeqResetCycle ruleSeqResetCycle) {
        this.numberRuleSlot1Type = slot1Type;
        this.numberRuleSlot1Custom = slot1Custom;
        this.numberRuleSlot2Type = slot2Type;
        this.numberRuleSlot2Custom = slot2Custom;
        this.numberRuleSlot3Type = slot3Type;
        this.numberRuleSlot3Custom = slot3Custom;
        this.numberRuleDateFormat = dateFormat;
        this.numberRuleSeqDigits = seqDigits;
        this.numberRuleSeparator = separator;
        this.numberRuleSeqResetCycle = ruleSeqResetCycle;
    }
}
