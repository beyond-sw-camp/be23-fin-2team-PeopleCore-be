package com.peoplecore.company.dtos;

import com.peoplecore.company.domain.copilot.CopilotConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * AI Copilot 컨텍스트 부분 수정용 요청 DTO.
 * 각 필드는 null 허용 — null이면 해당 필드를 변경하지 않음 (PATCH 의미).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CopilotContextUpdateReqDto {

    private String orgSummary;
    private Map<String, String> glossary;
    private CopilotConfig copilotConfig;
}
