package com.peoplecore.holiday.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HolidayReqDto {

    @NotNull(message = "공휴일의 날짜값은 필수입니다.")
    private LocalDate date;

    @NotBlank(message = "공휴일의 이름값은 필수입니다.")
    private String holidayName;

    @NotNull(message = "공휴일의 반복여부 선택은 필수입니다.")
    private Boolean isRepeating;
}
