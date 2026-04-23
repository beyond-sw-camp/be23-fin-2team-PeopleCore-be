package com.peoplecore.vacation.dto;

import com.peoplecore.vacation.entity.PayType;
import com.peoplecore.vacation.entity.VacationBalance;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/* 휴가 사용 신청 모달용 - 본인이 Balance 보유한 활성 휴가 유형 */
/* remainingDays 는 음수 가능 (선사용 허용 회사의 연차/월차) */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MyVacationTypeResponseDto {

    /* 유형 ID - 결재 docData 의 infoId 로 사용 */
    private Long typeId;

    /* 유형 코드 - MONTHLY/ANNUAL/회사정의 코드 */
    private String typeCode;

    /* 화면 표시명 */
    private String typeName;

    /* 1회 신청 단위 - 1.0 종일 / 0.5 반차 / 0.25 반반차. 프론트 반차 UI 제어 */
    private BigDecimal deductUnit;

    /* 유급/무급 구분 */
    private PayType payType;

    /* 회기 연도 - 적립 당시 달력연도. HIRE 정책에선 실사용 연도와 다를 수 있음 (유효성은 expires_at 기준) */
    private Integer balanceYear;

    /* 휴가 잔여량 = total - used - pending - expired. 음수 허용 */
    private BigDecimal remainingDays;

    /* 선사용 허용 여부 - true 면 remainingDays 음수여도 신청 가능 */
    /* 조건: 회사정책 allowAdvanceUse=true AND 유형이 연차/월차. 그 외는 false */
    private boolean allowAdvance;

    public static MyVacationTypeResponseDto from(VacationBalance b, boolean allowAdvance) {
        return MyVacationTypeResponseDto.builder()
                .typeId(b.getVacationType().getTypeId())
                .typeCode(b.getVacationType().getTypeCode())
                .typeName(b.getVacationType().getTypeName())
                .deductUnit(b.getVacationType().getDeductUnit())
                .payType(b.getVacationType().getPayType())
                .balanceYear(b.getBalanceYear())
                .remainingDays(b.getAvailableDays())
                .allowAdvance(allowAdvance)
                .build();
    }
}
