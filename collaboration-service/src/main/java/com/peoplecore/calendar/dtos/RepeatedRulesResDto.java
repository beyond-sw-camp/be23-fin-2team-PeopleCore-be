package com.peoplecore.calendar.dtos;

import com.peoplecore.calendar.entity.RepeatedRules;
import com.peoplecore.calendar.enums.Frequency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RepeatedRulesResDto {

    private Long repeatedRulesId;
    private Frequency frequency;
    private Integer intervalVal;    //간격
    private String byDay;
    private String byMonthDay;
    private LocalDate until;
    private Integer count;

    public static RepeatedRulesResDto fromEntity(RepeatedRules rules) {
        return RepeatedRulesResDto.builder()
                .repeatedRulesId(rules.getRepeatedRulesId())
                .frequency(rules.getFrequency())
                .intervalVal(rules.getIntervalVal())
                .byDay(rules.getByDay())
                .byMonthDay(rules.getByMonthDay())
                .until(rules.getUntil())
                .count(rules.getCount())
                .build();
    }
}
