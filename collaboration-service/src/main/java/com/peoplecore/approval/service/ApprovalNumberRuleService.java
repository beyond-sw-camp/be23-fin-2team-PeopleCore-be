package com.peoplecore.approval.service;

import com.peoplecore.approval.dto.NumberRuleResponse;
import com.peoplecore.approval.dto.NumberRuleUpdateRequest;
import com.peoplecore.approval.entity.ApprovalNumberRule;
import com.peoplecore.approval.repository.ApprovalNumberRuleRepository;
import com.peoplecore.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
@Slf4j
public class ApprovalNumberRuleService {

    private final ApprovalNumberRuleRepository numberRuleRepository;

    @Autowired
    public ApprovalNumberRuleService(ApprovalNumberRuleRepository numberRuleRepository) {
        this.numberRuleRepository = numberRuleRepository;
    }

    /*채번 규칙 조회*/
    public NumberRuleResponse getNumberRule(UUID companyId) {
        ApprovalNumberRule rule = numberRuleRepository.findByNumberRuleCompanyId(companyId).orElseThrow(() -> new BusinessException("채번 규칙이 설정되지 않았습니다. ", HttpStatus.NOT_FOUND));

        String preview = generatePreview(rule);
        return NumberRuleResponse.from(rule, preview);
    }

    /*채번 규칙 수정 */
    @Transactional
    public void updateNumberRule(UUID companyId, Long empId, NumberRuleUpdateRequest request) {
        ApprovalNumberRule rule = numberRuleRepository.findByNumberRuleCompanyId(companyId).orElseThrow(() -> new BusinessException("채번 규칙이 설정되지 않았습니다. ", HttpStatus.NOT_FOUND));

        rule.updateRule(
                request.getNumberRuleSlot1Type(),
                request.getNumberRuleSlot1Custom(),
                request.getNumberRuleSlot2Type(),
                request.getNumberRuleSlot2Custom(),
                request.getNumberRuleSlot3Type(),
                request.getNumberRuleSlot3Custom(),
                request.getNumberRuleDateFormat(),
                request.getNumberRuleSeqDigits(),
                request.getNumberRuleSeparator(),
                ApprovalNumberRule.NumberRuleSeqResetCycle.valueOf(request.getNumberRuleSeqResetCycle())
        );
    }


    /*미리보기 생성 */
    private String generatePreview(ApprovalNumberRule rule) {
        String sep  = rule.getNumberRuleSeparator();
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern(rule.getNumberRuleDateFormat()));
        String seq  = String.format("%0" + rule.getNumberRuleSeqDigits() + "d", 1);

        String slot1 = rule.getNumberRuleSlot1Custom() != null
                ? rule.getNumberRuleSlot1Custom() : rule.getNumberRuleSlot1Type();
        String slot2 = rule.getNumberRuleSlot2Custom() != null
                ? rule.getNumberRuleSlot2Custom() : rule.getNumberRuleSlot2Type();
        String slot3 = rule.getNumberRuleSlot3Custom() != null
                ? rule.getNumberRuleSlot3Custom() : rule.getNumberRuleSlot3Type();

        // NONE이면 미리보기에서 제외
        List<String> parts = Stream.of(slot1, slot2, slot3, date, seq)
                .filter(s -> s != null && !s.equals("NONE"))
                .toList();

        return String.join(sep, parts);
    }

}
