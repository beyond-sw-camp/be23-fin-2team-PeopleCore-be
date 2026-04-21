package com.peoplecore.vacation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/* 사원 단위 관리자 조정(MANUAL_GRANT) 합계 projection */
/* 용도: 부서별 사원 상세 테이블에서 "조정" 컬럼 표시 (발생 = 총연차 - 조정) */
/* JPQL DTO projection 으로 생성 - 엔티티 로드 없이 스칼라만 반환해 N+1 원천 차단 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManualGrantSumQueryDto {

    /* 사원 ID - GROUP BY 키 */
    private Long empId;

    /* 관리자 수동 부여/차감 합계 (부호 포함). 조정 없는 사원은 결과 row 자체 없음 */
    private BigDecimal adjustedDays;
}
