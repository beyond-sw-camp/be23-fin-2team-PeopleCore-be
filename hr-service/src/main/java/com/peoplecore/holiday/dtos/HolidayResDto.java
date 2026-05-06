package com.peoplecore.holiday.dtos;
import com.peoplecore.entity.HolidayType;
import com.peoplecore.entity.Holidays;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HolidayResDto {

    private Long holidayId;
    private LocalDate date;            // 원본 저장값
    private LocalDate occurrenceDate;  // year 기준 실제 발생일 (반복 휴일 매핑)
    private String holidayName;
    private HolidayType holidayType;
    private Boolean isRepeating;
    private UUID companyId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 단건 변환 - occurrenceDate 는 호출부에서 year 받아 계산해 주입
    public static HolidayResDto fromEntity(Holidays h, LocalDate occurrenceDate) {
        return HolidayResDto.builder()
                .holidayId(h.getHolidayId())
                .date(h.getDate())
                .occurrenceDate(occurrenceDate)
                .holidayName(h.getHolidayName())
                .holidayType(h.getHolidayType())
                .isRepeating(h.getIsRepeating())
                .companyId(h.getCompanyId())
                .createdAt(h.getCreatedAt())
                .updatedAt(h.getUpdatedAt())
                .build();
    }
}
