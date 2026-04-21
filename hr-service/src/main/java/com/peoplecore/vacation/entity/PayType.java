package com.peoplecore.vacation.entity;

/* 휴가 유급/무급 구분 - 급여 계산 시 차감 근거 */
/* PAID: 유급 (연차/월차/출산/배우자출산/유산사산/공가)                  */
/* UNPAID: 무급 (가족돌봄/생리)                                           */
public enum PayType {
    /* 유급 - 휴가일에도 급여 정상 지급 */
    PAID,
    /* 무급 - 휴가일은 급여 차감 대상 (LeaveAllowanceService 에서 참조) */
    UNPAID
}
