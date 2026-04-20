package com.peoplecore.evaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// 5번 강제배분 응답 DTO (수동재산정 리셋용)
// - noChange: cohort(참여자 수) 변화 없음 -> no-op
// - requiresConfirm: 보정 이력 존재 -> 프론트 확인 팝업 후 ?confirm=true 재호출
// - success=true: 실제 배분/리셋 수행 완료
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class DistributionApplyResultDto {
    private boolean success;          // true = 배분 완료
    private boolean noChange;         // cohort 변화 없음 -> 실행 안 함
    private boolean requiresConfirm;  // 확인 필요 (보정 리셋 경고)
    private int pendingResetCount;    // requiresConfirm=true 시 삭제 예정 보정 건수
    private int distributedCount;     // success=true 시 실제 배분된 인원
    private int resetCount;           // success=true 시 실제 리셋된 보정 건수
}
