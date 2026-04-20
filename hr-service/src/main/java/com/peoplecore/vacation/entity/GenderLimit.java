package com.peoplecore.vacation.entity;

import com.peoplecore.employee.domain.EmpGender;

/* 휴가 유형 성별 제한 - 신청 가능한 사원 성별 */
/* 생리/출산전후/유산사산: FEMALE_ONLY                                  */
/* 배우자출산: MALE_ONLY                                                */
/* 나머지: ALL                                                          */
public enum GenderLimit {
    /* 성별 제한 없음 - 모든 사원 신청 가능 */
    ALL,
    /* 여성만 가능 */
    FEMALE_ONLY,
    /* 남성만 가능 */
    MALE_ONLY;

    /* 사원 성별이 이 제한을 통과하는지 검사 - VacationRequestService.apply() 에서 호출 */
    /* 통과: true / 차단: false → VACATION_TYPE_GENDER_NOT_ALLOWED 예외 발생시킬 것 */
    public boolean allows(EmpGender empGender) {
        return switch (this) {
            case ALL         -> true;
            case FEMALE_ONLY -> empGender == EmpGender.FEMALE;
            case MALE_ONLY   -> empGender == EmpGender.MALE;
        };
    }
}
