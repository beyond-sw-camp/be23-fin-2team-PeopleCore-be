package com.peoplecore.evaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// 7번 - 보정 페이지 사원 목록 항목
//   autoGrade 가 보정 시 덮어써지므로, 원본은 Calibration 이력의 첫 fromGrade 로 복원
//   사유는 최신 1건만 표시 (전체 이력은 8번 API 에서 건별 조회)
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
    private String autoGrade;        // 자동등급 (원본 - 보정 시 Calibration 첫 fromGrade)
    private String adjustedGrade;    // 보정등급 (보정된 경우 현재 autoGrade, 아니면 null)
    private String reason;           // 보정 사유 (최신 1건만)
    private String adjusterName;     // 보정 수행자 이름 (최신 Calibration.actor)
    private boolean isCalibrated;    // 보정 여부 (행 강조 표시용)
}
