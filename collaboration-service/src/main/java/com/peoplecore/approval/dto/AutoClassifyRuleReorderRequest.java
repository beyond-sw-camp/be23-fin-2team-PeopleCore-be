package com.peoplecore.approval.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AutoClassifyRuleReorderRequest {

    private List<ReorderItem> orderList;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReorderItem {
        private Long id;
        private Integer sortOrder;
    }
}
