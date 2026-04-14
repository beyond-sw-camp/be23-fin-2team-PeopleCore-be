package com.peoplecore.evaluation.dto;

import com.peoplecore.evaluation.domain.Stage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// 단계 조회용
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StageDto {
    private Long id; // 단계 PK (DB의 stage_id)
    private String name;  // 단계명 (목표등록/자기평가/상위자평가/등급산정/결과확정)
    private Integer orderNo; // 순서 (1,2,3,…)
    private LocalDate startDate;
    private LocalDate endDate;
    private String status; // 대기/진행중/마감 (조회 시점 기준)

    public static StageDto from(Stage s) {
        return StageDto.builder()
                .id(s.getStageId())
                .name(s.getName())
                .orderNo(s.getOrderNo())
                .startDate(s.getStartDate())
                .endDate(s.getEndDate())
//              조회 시점 기준 실제 상태
                .status(s.calcStatusLabel())
                .build();
    }
}
