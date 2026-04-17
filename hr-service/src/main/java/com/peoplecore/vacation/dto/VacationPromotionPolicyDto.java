package com.peoplecore.vacation.dto;

import com.peoplecore.vacation.entity.VacationPolicy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/* 연차 촉진 정책 DTO - 조회/변경 공통 */
/* 화면: 촉진 사용 토글 + 1차 통지 N개월 셀렉트 + 2차 통지 N개월 셀렉트 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationPromotionPolicyDto {

    /* 촉진 정책 전체 사용 여부. false 면 1차/2차 무시됨 */
    private Boolean isActive;

    /* 1차 통지 시기 - 만료 N개월 전. NULL = 1차 비활성 */
    private Integer firstMonthsBefore;

    /* 2차 통지 시기 - 만료 N개월 전. NULL = 2차 비활성. 1차 활성일 때만 유효 */
    private Integer secondMonthsBefore;

    /* 엔티티 → DTO */
    public static VacationPromotionPolicyDto from(VacationPolicy policy) {
        return VacationPromotionPolicyDto.builder()
                .isActive(Boolean.TRUE.equals(policy.getIsPromotionActive()))
                .firstMonthsBefore(policy.getFirstNoticeMonthsBefore())
                .secondMonthsBefore(policy.getSecondNoticeMonthsBefore())
                .build();
    }
}