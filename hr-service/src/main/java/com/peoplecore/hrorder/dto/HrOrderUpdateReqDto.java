package com.peoplecore.hrorder.dto;

import com.peoplecore.hrorder.domain.OrderType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class HrOrderUpdateReqDto {
    // 수정할 발령유형: PROMOTION(승진) / TRANSFER(전보) / TITLE_CHANGE(보직변경)
    @NotNull(message = "발령유형은 필수입니다")
    private OrderType orderType;

    // 수정할 발령일: 스케줄러가 해당날짜에 employee 테이블 반영
    @NotNull(message = "발령일은 필수입니다")
    private LocalDate effectiveDate;

    // 수정할 변경 상세 목록
    // CreateReqDto.DetailItem 재사용 (empId, targetType, beforeId, afterId)
    @NotEmpty(message = "대상자는 1명 이상이어야 합니다")
    private List<HrOrderCreateReqDto.DetailItem> details;

    // 수정할 동적 폼 입력값
    // ex. { "orderTitle": "수정된 발령 제목", "orderReason": "부서 통합" }
    private Map<String, String> formValues;
}
