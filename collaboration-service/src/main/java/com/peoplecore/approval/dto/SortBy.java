package com.peoplecore.approval.dto;

/** 문서함 목록 정렬 기준 */
public enum SortBy {
    /** 최신순 (createdAt DESC) — 기본값 */
    LATEST,
    /** 긴급 우선 + 최신순 (isEmergency DESC, createdAt DESC) */
    EMERGENCY
}
