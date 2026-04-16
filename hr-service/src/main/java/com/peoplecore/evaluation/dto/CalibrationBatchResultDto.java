package com.peoplecore.evaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class CalibrationBatchResultDto {
    private boolean success; //저장 성공 여부
    private int saveCount; // 저장된 보정 건수
    private List<DistributionDiffDto.GradeDiff> currentDiff; //실패 시 현재 분포 응답용
}
