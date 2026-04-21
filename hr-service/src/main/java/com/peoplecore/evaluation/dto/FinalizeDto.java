package com.peoplecore.evaluation.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

// 12번 - 최종 확정 요청/응답 공용 DTO
//   req: acknowledgedEmpIds (확인 체크한 미산정자 empId)
//   res: finalizedAt, lockedCount
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinalizeDto {
    private List<Long> acknowledgedEmpIds;  // 요청 - 미산정자 체크용
    private LocalDateTime finalizedAt; //확정시간
    private int lockedCount; //잠금 처리된 배정 인원수
}
