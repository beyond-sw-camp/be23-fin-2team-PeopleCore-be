package com.peoplecore.evaluation.dto;

import com.peoplecore.evaluation.domain.MyResultStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 본인 평가결과 - 드롭다운용 시즌 옵션
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MySeasonOptionDto {
    private Long seasonId;
    private String name;
    private MyResultStatus status;       // IN_PROGRESS / FINALIZED
    private LocalDateTime finalizedAt;
}
