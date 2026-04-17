package com.peoplecore.vacation.dto;

import com.peoplecore.vacation.entity.VacationPolicy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/* 연차 지급 기준 DTO - 조회/변경 공통 */
/* 화면: 라디오 "입사일 기준(HIRE)" / "회계연도 기준(FISCAL)" + FISCAL 선택 시 mm-dd 입력 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationGrantBasisDto {

    /* 연차 발생 기준 - "HIRE" / "FISCAL" (PolicyBaseType enum name) */
    private String grantBasis;

    /* 회계연도 시작일 (mm-dd). FISCAL 일 때만 필수, HIRE 면 null */
    /* PolicyBaseType.FISCAL.resolveFiscalStart 에서 형식/필수 검증 */
    private String fiscalYearStart;

    /* 엔티티 → DTO 변환 - 조회 응답용 */
    public static VacationGrantBasisDto from(VacationPolicy policy) {
        return VacationGrantBasisDto.builder()
                .grantBasis(policy.getPolicyBaseType().name())
                .fiscalYearStart(policy.getPolicyFiscalYearStart())
                .build();
    }
}