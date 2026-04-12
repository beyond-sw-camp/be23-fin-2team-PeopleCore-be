package com.peoplecore.resign.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class ResignStatusDto {
    private long processableCount; //퇴직처리 대기 건 수 (ACTIVE)
    private long confirmedCount; //퇴직예정 건 수 (CONFIRMED, 스케줄러 대기)
    private long completedCount; //퇴직완료 건 수 (RESIGNED)

}
