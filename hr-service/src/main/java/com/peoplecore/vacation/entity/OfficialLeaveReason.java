package com.peoplecore.vacation.entity;

/* 공가(OFFICIAL_LEAVE) 하위 사유 - 공가 신청 시 사유 구분 통계용 */
/* 법적으로는 "공가" 하나로 묶이나 증빙 체계·통계 구분 위해 서브타입 유지 */
public enum OfficialLeaveReason {
    /* 예비군 훈련 - 예비군법 §10, 통지서 증빙 */
    RESERVE_FORCES,
    /* 민방위 훈련 - 민방위기본법 §27, 소집통보 증빙 */
    CIVIL_DEFENSE,
    /* 공민권 행사 - 근기법 §10, 선거·투표 등 */
    CIVIC_DUTY,
    /* 법원 출석 - 증인·배심원 등 법원 출석요구서 증빙 */
    COURT,
    /* 국가 건강검진 - 검진 확인서 증빙 */
    HEALTH_CHECK,
    /* 기타 공적 의무 - 위 분류 외 */
    ETC
}
