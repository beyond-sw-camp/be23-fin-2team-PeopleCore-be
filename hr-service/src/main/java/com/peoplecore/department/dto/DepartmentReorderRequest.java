package com.peoplecore.department.dto;

import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class DepartmentReorderRequest {

    private List<Item> items;

    @Data
    @AllArgsConstructor
    @Builder
    @NoArgsConstructor
    public static class Item {
        private Long deptId;
        private Long parentDeptId;
        private Integer sortOrder;
    }
}
