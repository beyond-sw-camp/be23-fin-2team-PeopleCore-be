package com.peoplecore.evaluation.dto;

import lombok.*;

// autoGrade 별 인원 집계 DTO
@NoArgsConstructor
@Data
@Builder
@AllArgsConstructor
public class AutoGradeCountDto {
    private String label;  // autoGrade 라벨 (S/A/B/C/D)
    private long count;    // 해당 등급 인원
}
