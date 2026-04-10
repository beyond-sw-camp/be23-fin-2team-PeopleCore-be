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
    private long processableCount; //퇴직처리 가능 건 수 (결재완료+재직) -null인 경우X
    private long completedCount; //퇴직완료건 수
    private long pendingCount; //결재대기 건 수

}
