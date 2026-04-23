package com.peoplecore.resign.domain;

public enum RetireStatus {
    ACTIVE,     //확정전(전자결재 상신후 확정전)
    CONFIRMED,  //대기(확정후 퇴사일 전 대기)
    RESIGNED    //퇴사
}