package com.peoplecore.approval.dto;

import lombok.*;

import java.util.List;

@Getter
@NoArgsConstructor
public class ApprovalFormReorderRequest {
    private List<FormOrder> orderList;

    @AllArgsConstructor
    @Builder
    @Data
    @NoArgsConstructor
    public static class FormOrder {
        private Long formId;
        private Integer formSortOrder;
    }
}