package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeveranceListResDto {
//   목록 + 요약

    private long totalCount;                // 전체 건수
    private long calculatingCount;          // 산정중
    private long confirmedCount;            // 확정
    private long approvedCount;             // 승인완료
    private long paidCount;                 // 지급완료
    private long totalSeveranceAmount;      // 퇴직금 총액
    private long totalNetAmount;            // 실지급 총액
    private Page<SeveranceResDto> severances;  // 페이징된 목록
}
