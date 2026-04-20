package com.peoplecore.evaluation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// 팀장 자기평가 반려 요청 - 사유 필수
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SelfEvaluationRejectRequest {
    @NotBlank
    private String rejectReason;
}
