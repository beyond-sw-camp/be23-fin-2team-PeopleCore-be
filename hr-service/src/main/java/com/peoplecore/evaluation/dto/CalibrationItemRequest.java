package com.peoplecore.evaluation.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class CalibrationItemRequest {
    private Long gradeId; //EvalGrade pk
    private String toGrade; //변경할 등급
    private String reason; //보정 사유
}
