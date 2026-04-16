package com.peoplecore.evaluation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

// 시즌 생성 요청 — 시즌 기본정보 + 5단계 일정까지 한 번에
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeasonCreateRequestDto {

    @NotBlank(message = "시즌명은 필수입니다")
    private String name;

    @NotBlank(message = "기간 구분은 필수입니다") // 상반기/하반기/연간
    private String period;

    @NotNull(message = "시작일은 필수입니다")
    private LocalDate startDate;

    @NotNull(message = "종료일은 필수입니다")
    private LocalDate endDate;

    // 단계 일정 5개 — 순서대로 (목표등록 / 자기평가 / 상위자평가 / 등급 산정 및 보정 / 결과확정)
    @NotEmpty(message = "단계 일정은 필수입니다")
    @Valid
    private List<StageInput> stages;


    // 단계 입력 — 이름은 백엔드가 고정 주입하므로 날짜만 받음
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StageInput {
        @NotNull(message = "단계 시작일은 필수입니다")
        private LocalDate startDate;

        @NotNull(message = "단계 종료일은 필수입니다")
        private LocalDate endDate;
    }
}
