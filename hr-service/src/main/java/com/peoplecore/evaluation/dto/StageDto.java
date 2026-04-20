package com.peoplecore.evaluation.dto;

import com.peoplecore.evaluation.domain.Stage;
import com.peoplecore.evaluation.domain.StageStatus;
import com.peoplecore.evaluation.domain.StageType;
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
    private Long id;             // 단계 PK (DB의 stage_id)
    private String name;         // 단계명 (EVALUATION 만 값, 고정 단계는 null — FE 가 type 으로 라벨 매핑)
    private Integer orderNo;     // 순서 (1,2,3,…)
    private StageType type;      // 시스템 단계 타입 (GOAL_ENTRY/EVALUATION/GRADING/FINALIZATION)
    private LocalDate startDate;
    private LocalDate endDate;
    private StageStatus status;  // WAITING / IN_PROGRESS / FINISHED

    public static StageDto from(Stage s) {
        return StageDto.builder()
                .id(s.getStageId())
                .name(s.getName())
                .orderNo(s.getOrderNo())
                .type(s.getType())
                .startDate(s.getStartDate())
                .endDate(s.getEndDate())
                .status(s.getStatus())
                .build();
    }
}
