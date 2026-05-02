package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollSyncResultResDto {
    private int addedCount;            // 이번 동기화로 추가된 사원 수
    private int totalEmployeesAfter;   // 동기화 후 급여대장 총 사원 수
}
