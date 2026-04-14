package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApprovedOvertimeResDto {
//    승인된 전자결재 응답

    private List<OvertimeItemDto> items;
    private Long totalAmount;   //합계금액

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OvertimeItemDto{
        private Long otId;
        private Integer otTypeFlag;
        private String otTypeLabel;
        private LocalDate otDate;



    }

}
