package com.peoplecore.vacation.dto;

import com.peoplecore.vacation.entity.VacationPolicy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/* 연차 지급 기준 DTO - 조회/변경 공통 */
/* 화면: 라디오 "입사일 기준(HIRE)" / "회계연도 기준(FISCAL)". 회계연도 시작일은 01-01 고정 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationGrantBasisDto {

    /* 연차 발생 기준 - "HIRE" / "FISCAL" (PolicyBaseType enum jobTypeName) */
    private String grantBasis;

    /* 회계연도 시작일 - FISCAL 이면 항상 "01-01", HIRE 면 null. 요청 시 값은 무시되고 서버가 강제 */
    /* 정책: 회사별 커스텀 폐지 → 모든 회사 공통 01-01 (VacationPolicy.FIXED_FISCAL_START) */
    private String fiscalYearStart;

    /* 엔티티 → DTO 변환 - 조회 응답용 */
    public static VacationGrantBasisDto from(VacationPolicy policy) {
        return VacationGrantBasisDto.builder()
                .grantBasis(policy.getPolicyBaseType().name())
                .fiscalYearStart(policy.getPolicyFiscalYearStart())
                .build();
    }
}