package com.peoplecore.evaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 8번 - 보정 이력 항목 (건별)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CalibrationHistoryDto {
    private Long calibrationId;      // 보정이력 PK
    private Long gradeId;            // EvalGrade PK (대상 식별)
    private String empNum;           // 사번
    private String empName;          // 성명
    private String deptName;         // 부서 (스냅샷)
    private String fromGrade;        // 변경 전 등급
    private String toGrade;          // 변경 후 등급
    private String reason;           // 보정 사유
    private String adjusterName;     // 보정 수행자 이름
    private LocalDateTime createdAt; // 보정 일시
}
