package com.peoplecore.evaluation.dto;

import lombok.*;

import java.time.LocalDate;

// 단계 날짜 수정 (재활용 DTO)
//  1) 기간 추가 (StageOpenClose): endDate 만 전달 → endDate 연장
//  2) SeasonDetail 자유 수정: startDate + endDate 둘 다 전달 → 통째로 교체
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StageUpdateRequestDto {
    private LocalDate startDate; // optional
    private LocalDate endDate;   // optional
}
