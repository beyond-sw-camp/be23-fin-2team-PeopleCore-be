package com.peoplecore.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CopilotResponse {

    private String answer;                  // LLM 최종 텍스트 응답
    private List<Citation> citations;       // 답변 근거가 된 문서 (UI에서 클릭 가능 링크 생성용)
    private List<ToolCall> toolCalls;       // 투명성 — LLM이 어떤 도구를 어떻게 호출했는지
    private List<Action> actions;           // FE 가 자동 실행할 클라이언트 사이드 액션 (모달 오픈 등)
    private String stopReason;              // "end_turn" | "tool_use" | "max_tokens" | "max_iterations"
    private Map<String, Integer> usage;     // {inputTokens, outputTokens} — 합산
    private String model;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class Citation {
        private String id;       // EMPLOYEE_13
        private String type;     // EMPLOYEE/DEPARTMENT/APPROVAL/CALENDAR
        private String title;
        private String link;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ToolCall {
        private String name;                  // "search_documents"
        private Map<String, Object> input;    // {keyword, type, size}
        private int resultCount;
    }

    /**
     * 클라이언트가 응답 수신 직후 자동 실행할 액션. 현재 지원: OPEN_APPROVAL_FORM.
     * payload 내부 구조는 type 별로 다르며 FE 가 type 분기로 처리.
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class Action {
        private String type;                   // "OPEN_APPROVAL_FORM"
        private Map<String, Object> payload;
    }
}
