package com.peoplecore.vacation.dto;

import com.peoplecore.vacation.entity.GenderLimit;
import com.peoplecore.vacation.entity.PayType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/* 휴가 유형 생성/수정 요청 DTO */
/* 생성 시: typeCode, typeName, deductUnit, sortOrder, payType, genderLimit 필요 */
/* 수정 시: typeCode 는 불변 (무시됨). 나머지 필드만 업데이트 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationTypeRequest {

    /* 회사 식별 코드 - 생성 시 필수. 시스템 예약 코드 사용 불가 */
    private String typeCode;

    /* 표시명 - 화면 노출용 */
    private String typeName;

    /* 1회 신청 단위 - 1.0=종일 / 0.5=반차 / 0.25=반반차 */
    private BigDecimal deductUnit;

    /* 화면 정렬 순서. null 이면 생성 시 999 */
    private Integer sortOrder;

    /* 성별 제한. null 이면 생성 시 ALL 로 저장 */
    private GenderLimit genderLimit;

    /* 유급/무급. null 이면 생성 시 PAID 로 저장 */
    private PayType payType;
}