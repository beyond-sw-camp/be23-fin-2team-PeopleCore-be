package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ApplyResultDto {
    private int appliedCount;
    private int skippedCount;
    private List<Long> skippedAllowanceIds;
}