package com.peoplecore.approval.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalFolderReorderRequest {
    /**
     * 순서 변경할 폴더 목록
     */
    private List<ReorderItem> orderList;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReorderItem {

        //    /*해당 문서함 id */
        private Long id;
        /*정렬 순서 */
        private Integer sortOrder;
    }
}
