package com.peoplecore.evaluation.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.evaluation.domain.EvaluationRules;
import com.peoplecore.evaluation.domain.Season;
import com.peoplecore.evaluation.domain.Stage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// 시즌 상세 조회용 (기본정보 + 단계목록 + 평가규칙)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeasonDetailDto {
    private Long id;
    private String name;
    private String period;                // 상반기/하반기/연간
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;                // 준비중/진행중/완료 (조회 시점 기준)
    private List<StageDto> stages;
    private EvaluationRulesDto rules;     // 시즌에 적용된 평가 규칙 (formSnapshot 기반)


    // Entity -> DTO 조립(rules 는 이미 RulesService 가 파싱한 DTO 를 주입)
    public static SeasonDetailDto from(Season s,
                                       List<Stage> stages,
                                       EvaluationRulesDto rulesDto) {
        List<StageDto> stageDtos = new ArrayList<>();
        for (Stage st : stages) {
            stageDtos.add(StageDto.from(st));
        }
        return SeasonDetailDto.builder()
                .id(s.getSeasonId())
                .name(s.getName())
                .period(s.getPeriod())
                .startDate(s.getStartDate())
                .endDate(s.getEndDate())
                .status(s.calcStatusLabel())           // 오늘 날짜 기준 계산
                .stages(stageDtos)
                .rules(rulesDto)
                .build();
    }
}
