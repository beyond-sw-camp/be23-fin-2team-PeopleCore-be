package com.peoplecore.evaluation.dto;

import com.peoplecore.evaluation.domain.Season;
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
    private String period; //상반기/하반기/연간
    private LocalDate startDate;
    private LocalDate endDate;
    private String status; //"준비중/진행중/ 완료

    public static SeasonResponseDto from(Season season){
        return SeasonResponseDto.builder()
                .id(season.getSeasonId())
                .name(season.getName())
                .period(season.getPeriod())
                .startDate(season.getStartDate())
                .endDate(season.getEndDate())
                .status(season.getStatus().getLabel()) // DB status enum → 한글 라벨
                .build();
    }
}
