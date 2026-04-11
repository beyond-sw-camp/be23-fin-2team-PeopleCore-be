package com.peoplecore.hrorder.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class HrOrderHistoryResDto {

    private Long orderId;
    private String orderType; //PROMOTION/TRANSFER/TITLE_CHANGE
    private String effectiveDate;
    private String status; //APPLIED/CONFIRMED/PENDING/REJECTED
    private String createAt;
    private List<DetailChange> detailChange;

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class DetailChange {
        private String targetType; //GRADE/ DEPARTMENT/ TITLE
        private String beforeName;
        private String afterName;
    }
}
