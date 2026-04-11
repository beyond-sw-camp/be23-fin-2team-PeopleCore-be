package com.peoplecore.listener;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.peoplecore.document.SearchDocument;
import com.peoplecore.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchEventListener {

    private final SearchService searchService;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    /**
     * 각 서비스에서 발행하는 이벤트 메시지 형식:
     * {
     *   "action": "CREATE" | "UPDATE" | "DELETE",
     *   "type": "EMPLOYEE" | "DEPARTMENT" | "APPROVAL" | "CALENDAR",
     *   "companyId": "1",
     *   "sourceId": "123",
     *   "title": "...",
     *   "content": "...",
     *   "metadata": { ... },
     *   "createdAt": "2026-04-12T10:00:00"
     * }
     */
    @KafkaListener(topics = "search-index-events", groupId = "search-service-group")
    public void handleSearchEvent(String message) {
        try {
            Map<String, Object> event = objectMapper.readValue(message, new TypeReference<>() {});

            String action = (String) event.get("action");
            String type = (String) event.get("type");
            String sourceId = (String) event.get("sourceId");

            if ("DELETE".equals(action)) {
                searchService.deleteDocument(sourceId, type);
                return;
            }

            SearchDocument document = SearchDocument.builder()
                    .id(type + "_" + sourceId)
                    .type(type)
                    .companyId((String) event.get("companyId"))
                    .sourceId(sourceId)
                    .title((String) event.get("title"))
                    .content((String) event.get("content"))
                    .metadata(event.get("metadata") != null
                            ? objectMapper.convertValue(event.get("metadata"), new TypeReference<>() {})
                            : null)
                    .createdAt(event.get("createdAt") != null
                            ? java.time.LocalDateTime.parse((String) event.get("createdAt"))
                            : java.time.LocalDateTime.now())
                    .build();

            searchService.indexDocument(document);

        } catch (Exception e) {
            log.error("Failed to process search event: {}", message, e);
        }
    }
}
