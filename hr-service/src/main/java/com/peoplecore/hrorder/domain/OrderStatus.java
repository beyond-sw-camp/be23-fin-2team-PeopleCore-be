package com.peoplecore.hrorder.domain;

public enum OrderStatus {
    PENDING,    //승인대기
    CONFIRMED,  //승인(HR_SUPER_ADMIN,발령일 대기)
    APPLIED,   //반영완료(스케줄러)
    REJECTED       // 반려
}
