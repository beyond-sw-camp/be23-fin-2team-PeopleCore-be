package com.peoplecore.pay.approval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Builder
public record ApprovalDraftResDto (
    ApprovalFormType type,
    Long ledgerId,     //(급여/퇴직급여) 대장,  SALARY 만 채워짐
    List<Long> sevIds,              //  RETIREMENT 만 채워짐
    String htmlTemplate,            // 결의서 양식 원문
    Map<String, String> dataMap     // data-key 매칭용
){}
