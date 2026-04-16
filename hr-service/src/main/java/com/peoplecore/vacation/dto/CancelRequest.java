package com.peoplecore.vacation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/* 취소 요청 body - 사유만 담는 단순 DTO */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancelRequest {

    /* 취소 사유 - Ledger.reason 에 기록 (APPROVED→CANCELED 시) */
    private String reason;
}