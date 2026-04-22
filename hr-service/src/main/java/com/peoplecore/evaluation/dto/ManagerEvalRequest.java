package com.peoplecore.evaluation.dto;

import lombok.*;

// 팀장평가 요청 DTO - 임시저장/제출 공용
//   임시저장: 모든 필드 nullable 허용 (부분 입력 상태 저장)
//   제출: 프론트에서 필드 채워서 호출 (서버는 값 그대로 세팅)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManagerEvalRequest {
    private String grade;      // S/A/B/C/D
    private String comment;    // 평가 코멘트 (내부용)
    private String feedback;   // 피드백 (당사자 공개)
}
