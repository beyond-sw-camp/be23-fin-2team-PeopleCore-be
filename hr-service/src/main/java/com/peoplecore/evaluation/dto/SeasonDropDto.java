package com.peoplecore.evaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// 드롭다운용 (id + name만)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeasonDropDto {
    private Long id;
    private String name;
}
