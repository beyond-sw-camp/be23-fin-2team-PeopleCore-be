package com.peoplecore.evaluation.dto;

import lombok.*;

import java.math.BigDecimal;

// 13번 - 평가 결과 목록 항목 (HR 전용, 확정 전/후 모두 조회)
//   isCalibrated = 보정 여부 (autoGrade != finalGrade 인 경우 표시용)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FinalGradeListItemDto {
    private Long gradeId;            // EvalGrade PK (14번 상세 이동용)
    private String empNum;
    private String empName;
    private String deptName;         // 부서 (확정 시점 스냅샷)
    private String position;         // 직급 (확정 시점 스냅샷)
    private BigDecimal totalScore;   // 종합점수
    private String autoGrade;        // 예정등급 (불변 원본)
    private String finalGrade;       // 확정등급 (보정 반영)
    private boolean isCalibrated;    // 보정 여부
}
