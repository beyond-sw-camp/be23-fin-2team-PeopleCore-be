package com.peoplecore.vacation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/* 사원 단위 특정 유형 balance 의 시작일/만료일 projection */
/* 용도: 부서 상세 테이블 "연차 사용기간" 컬럼 - balance.grantedAt ~ balance.expiresAt */
/* JPQL DTO projection 으로 엔티티 로드 없이 스칼라만 반환 (N+1 방지) */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BalanceExpiryQueryDto {

    /* 사원 ID - Map 키 */
    private Long empId;

    /* balance 최초 적립일 (회기 시작 또는 첫 적립 시점). null 이면 balance 없음 */
    private LocalDate grantedAt;

    /* balance 만료일. null 이면 무기한 또는 미설정 */
    private LocalDate expiresAt;
}
