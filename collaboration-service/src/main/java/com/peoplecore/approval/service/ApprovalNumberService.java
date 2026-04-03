package com.peoplecore.approval.service;

import com.peoplecore.approval.entity.ApprovalNumberRule;
import com.peoplecore.approval.entity.ApprovalSeqCounter;
import com.peoplecore.approval.repository.ApprovalNumberRuleRepository;
import com.peoplecore.approval.repository.ApprovalSeqCounterRepository;
import com.peoplecore.approval.slot.SlotContextDto;
import com.peoplecore.approval.slot.SlotTypeRegistry;
import com.peoplecore.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@Transactional
@Slf4j
public class ApprovalNumberService {
    private final ApprovalNumberRuleRepository numberRuleRepository;
    private final ApprovalSeqCounterRepository seqCounterRepository;
    private final SlotTypeRegistry slotTypeRegistry;

    @Autowired
    public ApprovalNumberService(ApprovalNumberRuleRepository numberRuleRepository, ApprovalSeqCounterRepository seqCounterRepository, SlotTypeRegistry slotTypeRegistry) {
        this.numberRuleRepository = numberRuleRepository;
        this.seqCounterRepository = seqCounterRepository;
        this.slotTypeRegistry = slotTypeRegistry;
    }

    @Transactional
    public String generateDocNum(UUID companyId, SlotContextDto contextDto) {
        try {
            return doGenerate(companyId, contextDto);
        } catch (DataIntegrityViolationException e) {
            return doGenerate(companyId, contextDto);
        }
    }

    private String doGenerate(UUID companyId, SlotContextDto contextDto) {
        /*1. 채번 규칙 조회*/
        ApprovalNumberRule rule = numberRuleRepository.findByNumberRuleCompanyId(companyId).orElseThrow(() -> new BusinessException("채번 규칙이 설정되지 않았습니다,"));

        /*리셋 키 계산 */
        String resetKey = resolveResetKey(rule.getNumberRuleSeqResetCycle());

        /*회사 규칙의 기간 카운터 가져오기 없으면 새로 생성 -> for update로 락 */
        ApprovalSeqCounter counter = seqCounterRepository.findWithLock(companyId, rule.getNumberRuleId(), resetKey).orElseGet(() -> seqCounterRepository.save(ApprovalSeqCounter.builder()
                .companyId(companyId)
                .seqRuleId(rule.getNumberRuleId())
                .seqResetKey(resetKey)
                .build()));

        /*채번*/
        int seq = counter.nextSeq();

        /*슬롯 값 변환 (타입을 실제 코드로 */
        String slot1 = slotTypeRegistry.find(rule.getNumberRuleSlot1Type(), rule.getNumberRuleSlot1Custom()).resolve(contextDto);
        String slot2 = slotTypeRegistry.find(rule.getNumberRuleSlot2Type(), rule.getNumberRuleSlot2Custom()).resolve(contextDto);
        String slot3 = slotTypeRegistry.find(rule.getNumberRuleSlot3Type(), rule.getNumberRuleSlot3Custom()).resolve(contextDto);

        /*번호 조립 */
        String date   = LocalDate.now().format(DateTimeFormatter.ofPattern(rule.getNumberRuleDateFormat()));
        String seqStr = String.format("%0" + rule.getNumberRuleSeqDigits() + "d", seq);

        List<String> parts = Stream.of(slot1, slot2, slot3, date, seqStr)
                .filter(Objects::nonNull)
                .toList();

        return String.join(rule.getNumberRuleSeparator(), parts);  // EX)"HR-LEAVE-260402-001"

    }

    private String resolveResetKey(ApprovalNumberRule.NumberRuleSeqResetCycle cycle) {
        return switch (cycle) {
            case YEAR  -> String.valueOf(LocalDate.now().getYear());
            case MONTH -> LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
            case NEVER -> "ALL";
        };
    }



}
