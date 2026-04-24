package com.peoplecore.evaluation.dto;

import com.peoplecore.evaluation.domain.MyResultStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 본인/팀장쪽 평가결과 - 드롭다운용 시즌 옵션
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MySeasonOptionDto {
    private Long seasonId;
    private String name;
    private MyResultStatus status;       // IN_PROGRESS / FINALIZED
    private LocalDateTime finalizedAt;
    private LocalDate startDate;         // 시즌 시작일 - 프론트에서 "오늘 이후" 필터링용
}
