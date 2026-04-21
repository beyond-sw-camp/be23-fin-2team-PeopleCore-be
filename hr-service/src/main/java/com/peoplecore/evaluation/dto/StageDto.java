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
    private String name;  // 단계명 (EVALUATION 만 값, 고정 단계는 null — FE 가 type 으로 라벨 매핑)
    private Integer orderNo; // 순서 (1,2,3,…)
    private String type; // 시스템 단계 타입 (GOAL_ENTRY/EVALUATION/GRADING/FINALIZATION)
    private LocalDate startDate;
    private LocalDate endDate;
    private String status; // 대기/진행중/마감 (조회 시점 기준)

    public static StageDto from(Stage s) {
        return StageDto.builder()
                .id(s.getStageId())
                .name(s.getName())
                .orderNo(s.getOrderNo())
                .type(s.getType() != null ? s.getType().name() : null)
                .startDate(s.getStartDate())
                .endDate(s.getEndDate())
//              enum 이름 그대로 (프론트에서 라벨 매핑)
                .status(s.getStatus().name())
                .build();
    }
}
