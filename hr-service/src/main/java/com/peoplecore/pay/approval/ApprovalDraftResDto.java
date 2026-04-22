package com.peoplecore.pay.approval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApprovalDraftResDto {
    private ApprovalFormType type;
    private Long ledgerId;      //(급여/퇴직급여) 대장
    private String htmlTemplate;          // 결의서 양식 원문
    private Map<String, String> dataMap;  // data-key 매칭용
}
