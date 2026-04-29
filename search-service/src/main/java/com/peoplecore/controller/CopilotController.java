package com.peoplecore.controller;

import com.peoplecore.dto.CopilotRequest;
import com.peoplecore.dto.CopilotResponse;
import com.peoplecore.llm.CopilotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI Copilot 채팅 엔드포인트. 권한·회사 컨텍스트는 SearchController 와 동일한 헤더 규약을 따른다
 * (gateway 가 강제 주입). API 키 미설정 시 503 으로 명시적으로 실패시켜 운영자가 즉시 인지하도록 한다.
 */
@Slf4j
@RestController
@RequestMapping("/copilot")
@RequiredArgsConstructor
public class CopilotController {

    private final CopilotService copilotService;

    @PostMapping("/chat")
    public ResponseEntity<?> chat(
            @RequestBody CopilotRequest req,
            @RequestHeader("X-User-Company") String companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestHeader("X-User-Role") String role
    ) {
        if (req == null || req.getMessage() == null || req.getMessage().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message is required"));
        }
        try {
            CopilotResponse resp = copilotService.chat(req, companyId, empId, role);
            return ResponseEntity.ok(resp);
        } catch (IllegalStateException e) {
            log.warn("copilot unavailable: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("copilot chat failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
