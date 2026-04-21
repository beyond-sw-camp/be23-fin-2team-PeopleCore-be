package com.peoplecore.vacation.dto;

import com.peoplecore.vacation.entity.VacationPolicy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/* 연차/월차 미리쓰기 허용 정책 DTO - 조회/변경 공통 */
/* 화면: 단일 토글 (허용/비허용). 법정휴가는 이 토글과 무관 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationAdvanceUsePolicyDto {

    /* 미리쓰기 허용 여부. true 면 연차/월차 잔여 부족해도 신청 가능 (available 음수 허용) */
    private Boolean isAllowed;

    /* 엔티티 → DTO */
    public static VacationAdvanceUsePolicyDto from(VacationPolicy policy) {
        return VacationAdvanceUsePolicyDto.builder()
                .isAllowed(policy.isAdvanceUseActive())
                .build();
    }
}
