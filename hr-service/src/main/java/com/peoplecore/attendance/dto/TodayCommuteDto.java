package com.peoplecore.attendance.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/*
 * 오늘 출퇴근 시각. 체크인 전이면 둘 다 null, 체크인만 찍은 상태면 checkOut null.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TodayCommuteDto {

    /* 출근 시각 — "HH:mm" 포맷 */
    @JsonFormat(pattern = "HH:mm")
    private LocalTime checkIn;

    /* 퇴근 시각 — "HH:mm" 포맷. 체크아웃 전이면 null */
    @JsonFormat(pattern = "HH:mm")
    private LocalTime checkOut;
}