package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PayrollDetailUpdateReqDto {
    private List<Item> items;

    @Data
    public static class Item {
        private Long payItemId;
        private Long amount;
    }
}
