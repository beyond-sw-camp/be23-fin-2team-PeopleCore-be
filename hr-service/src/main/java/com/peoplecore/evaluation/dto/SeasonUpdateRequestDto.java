package com.peoplecore.evaluation.dto;


import com.peoplecore.evaluation.domain.SeasonPeriod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class SeasonUpdateRequestDto {

    @NotBlank(message = "시즌명은 필수입니다")
    private String name;

    private SeasonPeriod period;

    @NotNull(message = "시작일은 필수입니다")
    private LocalDate startDate;

    @NotNull(message = "종료일은 필수입니다")
    private LocalDate endDate;

    // 옵션 — 동봉 시 시즌 + 단계 일정을 한 트랜잭션에서 원자 업데이트.
    // DRAFT 시즌의 단계 일정 동시 수정 케이스에 사용 (편집 화면의 새 상태 기준 검증)
    @Valid
    private List<StageInput> stages;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StageInput {
        @NotNull(message = "단계 ID는 필수입니다")
        private Long stageId;

        @NotNull(message = "단계 시작일은 필수입니다")
        private LocalDate startDate;

        @NotNull(message = "단계 종료일은 필수입니다")
        private LocalDate endDate;
    }
}

