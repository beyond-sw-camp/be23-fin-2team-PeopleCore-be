package com.peoplecore.evaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// 7번 - 보정 페이지 사원 목록 항목
//   autoGrade = EvalGrade.autoGrade (불변 원본)
//   adjustedGrade = EvalGrade.finalGrade (보정 반영, 미보정이면 null)
//   사유/수행자는 Calibration 이력의 최신 1건만 표시 (전체 이력은 8번 API)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CalibrationListItemDto {
    private Long gradeId;            // EvalGrade PK (보정 저장 시 식별자)
    private String empNum;           // 사번
    private String name;             // 성명
    private String deptName;         // 부서 (시즌 오픈 시 스냅샷)
    private String position;         // 직급 (스냅샷)
    private BigDecimal totalScore;   // 종합점수 (null 이면 미산정)
    private String autoGrade;        // 자동등급 (불변 원본 - EvalGrade.autoGrade)
    private String adjustedGrade;    // 보정등급 (보정된 경우 EvalGrade.finalGrade, 아니면 null)
    private String reason;           // 보정 사유 (최신 1건만)
    private String adjusterName;     // 보정 수행자 이름 (최신 Calibration.actor)
    private boolean isCalibrated;    // 보정 여부 (행 강조 표시용)
}
