package com.peoplecore.evaluation.dto;

import com.peoplecore.evaluation.domain.EvalSeasonStatus;
import com.peoplecore.evaluation.domain.Season;
import com.peoplecore.evaluation.domain.SeasonPeriod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;


//목록용
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class SeasonResponseDto {
    private Long id;
    private String name;
    private SeasonPeriod period;       // FIRST_HALF / SECOND_HALF / ANNUAL
    private LocalDate startDate;
    private LocalDate endDate;
    private EvalSeasonStatus status;   // DRAFT / OPEN / CLOSED 등

    public static SeasonResponseDto from(Season season){
        return SeasonResponseDto.builder()
                .id(season.getSeasonId())
                .name(season.getName())
                .period(season.getPeriod())
                .startDate(season.getStartDate())
                .endDate(season.getEndDate())
                .status(season.getStatus())
                .build();
    }
}
