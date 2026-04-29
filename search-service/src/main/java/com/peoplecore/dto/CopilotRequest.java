package com.peoplecore.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Copilot 채팅 요청. history는 멀티턴 컨텍스트 — 클라이언트가 매 호출마다 전체 누적 히스토리를 보냄(stateless 서버).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CopilotRequest {

    private String message;
    private List<HistoryTurn> history;

    /** FE 가 동봉하는 현재 화면 컨텍스트. system prompt 에 합성되어 화면-기반 발화 정확도를 올린다. */
    private PageContext pageContext;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoryTurn {
        private String role;     // "user" | "assistant"
        private String content;  // 텍스트만 — 과거 turn의 tool_use 블록은 직렬화하지 않음(요약본만)
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageContext {
        /** location.pathname (예: "/approval/123", "/hr/payroll") */
        private String route;
    }
}
