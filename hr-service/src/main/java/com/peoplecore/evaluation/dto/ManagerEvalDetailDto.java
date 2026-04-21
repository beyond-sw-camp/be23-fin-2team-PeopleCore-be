package com.peoplecore.evaluation.dto;

import lombok.*;

import java.time.LocalDateTime;

// 팀장평가 단건 조회 응답 (임시저장 복구/수정용)
//   평가 없으면 모든 필드 null 로 빈 DTO 반환
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ManagerEvalDetailDto {
    private String grade;              // S/A/B/C/D (없으면 null)
    private String comment;            // 평가 코멘트 (내부용)
    private String feedback;           // 피드백 (당사자 공개)
    private LocalDateTime submittedAt; // 제출 시각 (null = 임시저장 or 미시작)
}
