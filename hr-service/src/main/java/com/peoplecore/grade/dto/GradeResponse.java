package com.peoplecore.grade.dto;

import com.peoplecore.grade.domain.Grade;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GradeResponse {
    private Long gradeId;
    private String gradeName;
    private String gradeCode;
    private Integer gradeOrder;

    public static GradeResponse from(Grade grade) {
        return GradeResponse.builder()
                .gradeId(grade.getGradeId())
                .gradeName(grade.getGradeName())
                .gradeCode(grade.getGradeCode())
                .gradeOrder(grade.getGradeOrder())
                .build();
    }
}
