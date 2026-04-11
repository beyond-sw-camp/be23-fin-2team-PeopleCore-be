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
public class HrOrderCreateReqDto {

    @NotNull(message = "발령유형은 필수입니다")
    private OrderType orderType;

    @NotNull(message = "발령일은 필수입니다")
    private LocalDate effectiveDate;

    @NotEmpty(message = "대상자는 1명 이상이어야 합니다")
    private List<DetailItem> details;

//    동적 폼 입력값 ex. { "orderTitle": "2026년 상반기 인사발령", "orderReason": "정기 승진" }
    private Map<String,String>formValues;

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class DetailItem {
        @NotNull(message = "대상 사원 ID는 필수입니다")
        private Long empId;

        @NotNull(message = "변경 대상 구분은 필수입니다")
        private String targetType;

        @NotNull(message = "변경 전 ID는 필수입니다")
        private Long beforeId;

        @NotNull(message = "변경 후 ID는 필수입니다")
        private Long afterId;
    }

}
