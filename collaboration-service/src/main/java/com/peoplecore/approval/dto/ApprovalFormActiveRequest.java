package com.peoplecore.approval.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 양식 사용여부 토글 요청 DTO.
 * - 관리자 화면 사용여부 스위치 ON/OFF 시 PATCH /approval/forms/{formId}/active 로 전달
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalFormActiveRequest {
    /** true: 사용여부 ON, false: OFF (보호 양식은 OFF 차단) */
    private Boolean isActive;
}
