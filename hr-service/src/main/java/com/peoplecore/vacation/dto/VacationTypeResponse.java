package com.peoplecore.vacation.dto;

import com.peoplecore.vacation.entity.GenderLimit;
import com.peoplecore.vacation.entity.PayType;
import com.peoplecore.vacation.entity.VacationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/* 휴가 유형 응답 DTO */
/* isSystemReserved 플래그 - 프론트가 시스템 예약 유형 수정/삭제 버튼 비활성화 판단 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationTypeResponse {

    /* 유형 ID (PK) */
    private Long typeId;

    /* 회사 식별 코드 (시스템 예약 또는 회사 정의 코드) */
    private String typeCode;

    /* 표시명 */
    private String typeName;

    /* 1회 신청 단위 (1.0 / 0.5 / 0.25) */
    private BigDecimal deductUnit;

    /* 활성화 여부 */
    private Boolean isActive;

    /* 정렬 순서 */
    private Integer sortOrder;

    /* 성별 제한 (ALL / FEMALE_ONLY / MALE_ONLY) - 프론트 드롭다운 필터링용 */
    private GenderLimit genderLimit;

    /* 유급/무급 (PAID / UNPAID) - 신청 화면 표시용 */
    private PayType payType;

    /* 시스템 예약 유형 여부 - 프론트에서 수정/삭제 버튼 비활성화용 */
    private Boolean isSystemReserved;

    public static VacationTypeResponse from(VacationType type) {
        return VacationTypeResponse.builder()
                .typeId(type.getTypeId())
                .typeCode(type.getTypeCode())
                .typeName(type.getTypeName())
                .deductUnit(type.getDeductUnit())
                .isActive(type.getIsActive())
                .sortOrder(type.getSortOrder())
                .genderLimit(type.getGenderLimit())
                .payType(type.getPayType())
                .isSystemReserved(type.isSystemReserved())
                .build();
    }
}