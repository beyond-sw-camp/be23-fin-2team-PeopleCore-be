package com.peoplecore.cdc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.document.SearchDocument;
import com.peoplecore.embedding.EmbeddingClient;
import com.peoplecore.repository.SearchRepository;
import com.peoplecore.service.SearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Debezium MySQL CDC 토픽 소비자.
 *
 * 토픽 6개를 구독:
 * - peoplecore.peoplecore.employee          → EMPLOYEE 색인 (+ dept/grade/title lookup)
 * - peoplecore.peoplecore.department        → DEPARTMENT 색인 + cache 갱신
 * - peoplecore.peoplecore.grade             → cache 갱신만
 * - peoplecore.peoplecore.title             → cache 갱신만
 * - peoplecore.peoplecore.approval_document → APPROVAL 색인 (작성자 이름 denormalized)
 * - peoplecore.peoplecore.events            → CALENDAR 색인
 *
 * Debezium 이벤트 구조:
 *   payload.op    : "r"(snapshot) | "c"(create) | "u"(update) | "d"(delete)
 *   payload.before: 이전 row (delete/update)
 *   payload.after : 새 row   (create/update/snapshot)
 */
@Slf4j
@Component
public class CdcEventListener {

    private final SearchService searchService;
    private final SearchRepository searchRepository;
    private final CdcLookupCache cache;
    private final EmbeddingClient embeddingClient;
    private final ObjectMapper objectMapper;

    public CdcEventListener(SearchService searchService, SearchRepository searchRepository, CdcLookupCache cache, EmbeddingClient embeddingClient) {
        this.searchService = searchService;
        this.searchRepository = searchRepository;
        this.cache = cache;
        this.embeddingClient = embeddingClient;
        this.objectMapper = new ObjectMapper();
    }

    // =========================== Department ===========================

    @KafkaListener(topics = "peoplecore.peoplecore.department", groupId = "search-service-cdc")
    public void handleDepartment(String message) {
        try {
            JsonNode payload = extractPayload(message);
            if (payload == null) return;

            String op = payload.path("op").asText();
            JsonNode row = "d".equals(op) ? payload.path("before") : payload.path("after");
            if (row.isMissingNode() || row.isNull()) return;

            Long deptId = row.path("dept_id").asLong();
            String deptName = row.path("dept_name").asText(null);
            String companyId = CdcEventParser.decodeUuid(row.path("company_id").asText(null));
            String sourceId = String.valueOf(deptId);

            if ("d".equals(op)) {
                cache.removeDept(deptId);
                searchService.deleteDocument(sourceId, "DEPARTMENT");
                return;
            }

            // is_use=false(비활성 부서)도 색인은 유지 — 관리자는 보여야 하고,
            // 일반 사원에게는 SearchService.applyAccessFilter(isUse=true)가 쿼리 단계에서 숨김.
            boolean isUse = row.path("is_use").asBoolean(true);

            cache.putDept(deptId, deptName);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("deptCode", row.path("dept_code").asText(null));
            metadata.put("parentDeptId", row.hasNonNull("parent_dept_id") ? row.path("parent_dept_id").asLong() : null);
            metadata.put("isUse", isUse);
            metadata.put("link", "/org-management/department/" + deptId);

            String createdAt = CdcEventParser.decodeMicroTimestampIso((Number) asNumber(row.path("created_at")));

            SearchDocument doc = SearchDocument.builder()
                    .id("DEPARTMENT_" + sourceId)
                    .type("DEPARTMENT")
                    .companyId(companyId)
                    .sourceId(sourceId)
                    .title(deptName)
                    .content("")
                    .metadata(metadata)
                    .createdAt(createdAt)
                    .build();

            searchService.indexDocument(doc);

        } catch (Exception e) {
            log.error("Failed to process department CDC event", e);
        }
    }

    // =========================== Grade ===========================

    @KafkaListener(topics = "peoplecore.peoplecore.grade", groupId = "search-service-cdc")
    public void handleGrade(String message) {
        try {
            JsonNode payload = extractPayload(message);
            if (payload == null) return;

            String op = payload.path("op").asText();
            JsonNode row = "d".equals(op) ? payload.path("before") : payload.path("after");
            if (row.isMissingNode() || row.isNull()) return;

            Long gradeId = row.path("grade_id").asLong();
            String gradeName = row.path("grade_name").asText(null);

            if ("d".equals(op)) {
                // grade는 ES 색인 대상 아님, 캐시만 무효화 불필요 (그냥 유지)
                return;
            }
            cache.putGrade(gradeId, gradeName);

        } catch (Exception e) {
            log.error("Failed to process grade CDC event", e);
        }
    }

    // =========================== Title ===========================

    @KafkaListener(topics = "peoplecore.peoplecore.title", groupId = "search-service-cdc")
    public void handleTitle(String message) {
        try {
            JsonNode payload = extractPayload(message);
            if (payload == null) return;

            String op = payload.path("op").asText();
            JsonNode row = "d".equals(op) ? payload.path("before") : payload.path("after");
            if (row.isMissingNode() || row.isNull()) return;

            Long titleId = row.path("title_id").asLong();
            String titleName = row.path("title_name").asText(null);

            if ("d".equals(op)) return;
            cache.putTitle(titleId, titleName);

        } catch (Exception e) {
            log.error("Failed to process title CDC event", e);
        }
    }

    // =========================== Employee ===========================

    @KafkaListener(topics = "peoplecore.peoplecore.employee", groupId = "search-service-cdc")
    public void handleEmployee(String message) {
        try {
            JsonNode payload = extractPayload(message);
            if (payload == null) return;

            String op = payload.path("op").asText();
            JsonNode row = "d".equals(op) ? payload.path("before") : payload.path("after");
            if (row.isMissingNode() || row.isNull()) return;

            Long empId = row.path("emp_id").asLong();
            String sourceId = String.valueOf(empId);

            if ("d".equals(op)) {
                searchService.deleteDocument(sourceId, "EMPLOYEE");
                return;
            }

            // soft delete 처리: delete_at이 있으면 색인 제거
            if (row.hasNonNull("delete_at")) {
                searchService.deleteDocument(sourceId, "EMPLOYEE");
                return;
            }

            // RESIGNED(퇴사)는 ES에서 완전 제외.
            // ON_LEAVE(휴직)는 색인은 유지 — 관리자(HR_ADMIN/HR_SUPER_ADMIN)는 보여야 하고,
            // 일반 사원에게는 SearchService.applyAccessFilter(empStatus=ACTIVE)가 쿼리 단계에서 숨김 처리.
            String empStatus = row.path("emp_status").asText(null);
            if ("RESIGNED".equals(empStatus)) {
                searchService.deleteDocument(sourceId, "EMPLOYEE");
                return;
            }

            String empName = row.path("emp_name").asText(null);
            String companyId = CdcEventParser.decodeUuid(row.path("company_id").asText(null));

            Long deptId = row.hasNonNull("dept_id") ? row.path("dept_id").asLong() : null;
            Long gradeId = row.hasNonNull("grade_id") ? row.path("grade_id").asLong() : null;
            Long titleId = row.hasNonNull("title_id") ? row.path("title_id").asLong() : null;

            String deptName = cache.getDeptName(deptId);
            String gradeName = cache.getGradeName(gradeId);
            String titleName = cache.getTitleName(titleId);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("empName", empName);
            metadata.put("empEmail", row.path("emp_email").asText(null));
            metadata.put("empPhone", row.path("emp_phone").asText(null));
            metadata.put("empNum", row.path("emp_num").asText(null));
            metadata.put("empStatus", empStatus);
            metadata.put("empType", row.path("emp_type").asText(null));
            metadata.put("deptId", deptId);
            metadata.put("deptName", deptName);
            metadata.put("gradeName", gradeName);
            metadata.put("titleName", titleName);
            metadata.put("profileImageUrl", row.path("emp_profile_image_url").asText(null));
            metadata.put("link", "/org-management/employee/" + empId);

            String content = String.join(" ",
                    deptName != null ? deptName : "",
                    gradeName != null ? gradeName : "",
                    titleName != null ? titleName : ""
            ).trim();

            String createdAt = CdcEventParser.decodeDate((Number) asNumber(row.path("emp_hire_date")))
                    != null
                    ? CdcEventParser.decodeDate((Number) asNumber(row.path("emp_hire_date"))).atStartOfDay().toString()
                    : null;

            SearchDocument doc = SearchDocument.builder()
                    .id("EMPLOYEE_" + sourceId)
                    .type("EMPLOYEE")
                    .companyId(companyId)
                    .sourceId(sourceId)
                    .title(empName)
                    .content(content)
                    .metadata(metadata)
                    .createdAt(createdAt)
                    .build();

            searchService.indexDocument(doc);

        } catch (Exception e) {
            log.error("Failed to process employee CDC event", e);
        }
    }

    // =========================== Approval Document ===========================

    @KafkaListener(topics = "peoplecore.peoplecore.approval_document", groupId = "search-service-cdc")
    public void handleApproval(String message) {
        try {
            JsonNode payload = extractPayload(message);
            if (payload == null) return;

            String op = payload.path("op").asText();
            JsonNode row = "d".equals(op) ? payload.path("before") : payload.path("after");
            if (row.isMissingNode() || row.isNull()) return;

            Long docId = row.path("doc_id").asLong();
            String sourceId = String.valueOf(docId);

            if ("d".equals(op)) {
                cache.removeDoc(docId);
                searchService.deleteDocument(sourceId, "APPROVAL");
                return;
            }

            // DRAFT(임시저장), CANCELLED(회수)는 색인 제외
            String approvalStatus = row.path("approval_status").asText(null);
            if ("DRAFT".equals(approvalStatus) || "CANCELLED".equals(approvalStatus)) {
                searchService.deleteDocument(sourceId, "APPROVAL");
                return;
            }

            String companyId = CdcEventParser.decodeUuid(row.path("company_id").asText(null));
            String docTitle = row.path("doc_title").asText(null);
            String docType = row.path("doc_type").asText(null);
            String docNum = row.path("doc_num").asText(null);
            String empName = row.path("emp_name").asText(null);
            String empDeptName = row.path("emp_dept_name").asText(null);
            String empGrade = row.path("emp_grade").asText(null);
            String empTitle = row.path("emp_title").asText(null);
            Long drafterId = row.hasNonNull("emp_id") ? row.path("emp_id").asLong() : null;

            Set<Long> accessibleEmpIds = new HashSet<>();
            if (drafterId != null) accessibleEmpIds.add(drafterId);
            accessibleEmpIds.addAll(cache.getLineEmpIds(docId));

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("docNum", docNum);
            metadata.put("docType", docType);
            metadata.put("approvalStatus", approvalStatus);
            metadata.put("empName", empName);
            metadata.put("deptName", empDeptName);
            metadata.put("gradeName", empGrade);
            metadata.put("titleName", empTitle);
            metadata.put("isEmergency", row.path("is_emergency").asBoolean(false));
            metadata.put("drafterId", drafterId);
            metadata.put("accessibleEmpIds", new ArrayList<>(accessibleEmpIds));
            metadata.put("link", "/approval/" + docId);

            String content = String.join(" ",
                    empName != null ? empName : "",
                    empDeptName != null ? empDeptName : "",
                    docType != null ? docType : ""
            ).trim();

            String createdAt = CdcEventParser.decodeMicroTimestampIso((Number) asNumber(row.path("doc_submitted_at")));

            float[] contentVector = embedSafe(docTitle, content);

            SearchDocument doc = SearchDocument.builder()
                    .id("APPROVAL_" + sourceId)
                    .type("APPROVAL")
                    .companyId(companyId)
                    .sourceId(sourceId)
                    .title(docTitle)
                    .content(content)
                    .metadata(metadata)
                    .createdAt(createdAt)
                    .contentVector(contentVector)
                    .build();

            searchService.indexDocument(doc);

        } catch (Exception e) {
            log.error("Failed to process approval CDC event", e);
        }
    }

    // 임베딩 실패해도 색인은 진행 (벡터 없는 문서는 BM25로만 검색됨)
    private float[] embedSafe(String title, String content) {
        try {
            String combined = ((title != null ? title : "") + "\n" + (content != null ? content : "")).trim();
            if (combined.isBlank()) return null;
            return embeddingClient.embed(combined);
        } catch (Exception e) {
            log.warn("Embedding failed, indexing without vector: {}", e.getMessage());
            return null;
        }
    }

    // =========================== Calendar Events ===========================

    @KafkaListener(topics = "peoplecore.peoplecore.events", groupId = "search-service-cdc")
    public void handleEvent(String message) {
        try {
            JsonNode payload = extractPayload(message);
            if (payload == null) return;

            String op = payload.path("op").asText();
            JsonNode row = "d".equals(op) ? payload.path("before") : payload.path("after");
            if (row.isMissingNode() || row.isNull()) return;

            Long eventsId = row.path("events_id").asLong();
            String sourceId = String.valueOf(eventsId);

            if ("d".equals(op)) {
                searchService.deleteDocument(sourceId, "CALENDAR");
                return;
            }

            // soft delete 처리
            if (row.hasNonNull("deleted_at")) {
                searchService.deleteDocument(sourceId, "CALENDAR");
                return;
            }

            String companyId = CdcEventParser.decodeUuid(row.path("company_id").asText(null));
            String title = row.path("title").asText(null);
            String description = row.path("description").asText(null);
            String location = row.path("location").asText(null);

            Long ownerId = row.hasNonNull("emp_id") ? row.path("emp_id").asLong() : null;

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("location", location);
            metadata.put("empId", ownerId);
            metadata.put("ownerId", ownerId);
            metadata.put("isAllDay", row.path("is_all_day").asBoolean(false));
            metadata.put("isPublic", row.path("is_public").asBoolean(false));
            metadata.put("isAllEmployees", row.path("is_all_employees").asBoolean(false));
            metadata.put("startAt", CdcEventParser.decodeMicroTimestampIso((Number) asNumber(row.path("start_at"))));
            metadata.put("endAt", CdcEventParser.decodeMicroTimestampIso((Number) asNumber(row.path("end_at"))));
            metadata.put("link", "/calendar/" + eventsId);

            String content = String.join(" ",
                    description != null ? description : "",
                    location != null ? location : ""
            ).trim();

            String createdAt = CdcEventParser.decodeMicroTimestampIso((Number) asNumber(row.path("start_at")));

            SearchDocument doc = SearchDocument.builder()
                    .id("CALENDAR_" + sourceId)
                    .type("CALENDAR")
                    .companyId(companyId)
                    .sourceId(sourceId)
                    .title(title)
                    .content(content)
                    .metadata(metadata)
                    .createdAt(createdAt)
                    .build();

            searchService.indexDocument(doc);

        } catch (Exception e) {
            log.error("Failed to process events CDC event", e);
        }
    }

    // =========================== Approval Line ===========================
    //
    // 결재선(approval_line)은 문서(approval_document)와 별도 토픽이라 순서 보장이 안 되므로
    // 캐시(CdcLookupCache.lineEmpIdsByDoc)에 별도 저장 후, 문서 색인 시 합성한다.
    // line 이벤트가 doc 이벤트보다 뒤에 도착한 경우, 기존 문서를 다시 읽어 accessibleEmpIds만 갱신하여 재색인.

    @KafkaListener(topics = "peoplecore.peoplecore.approval_line", groupId = "search-service-cdc")
    public void handleApprovalLine(String message) {
        try {
            JsonNode payload = extractPayload(message);
            if (payload == null) return;

            String op = payload.path("op").asText();
            JsonNode row = "d".equals(op) ? payload.path("before") : payload.path("after");
            if (row.isMissingNode() || row.isNull()) return;

            Long lineId = row.path("line_id").asLong();
            Long docId = row.hasNonNull("doc_id") ? row.path("doc_id").asLong() : null;
            Long empId = row.hasNonNull("emp_id") ? row.path("emp_id").asLong() : null;
            if (docId == null) return;

            if ("d".equals(op)) {
                cache.removeLine(docId, lineId);
            } else {
                if (empId != null) cache.putLine(docId, lineId, empId);
            }

            // 기존 APPROVAL 문서가 이미 색인되어 있다면 accessibleEmpIds를 갱신하여 재색인
            Optional<SearchDocument> existing = searchRepository.findById("APPROVAL_" + docId);
            if (existing.isEmpty()) return;

            SearchDocument doc = existing.get();
            Map<String, Object> metadata = doc.getMetadata() != null
                    ? new HashMap<>(doc.getMetadata())
                    : new HashMap<>();

            Set<Long> accessibleEmpIds = new HashSet<>();
            Object drafterIdObj = metadata.get("drafterId");
            if (drafterIdObj instanceof Number n) accessibleEmpIds.add(n.longValue());
            accessibleEmpIds.addAll(cache.getLineEmpIds(docId));

            metadata.put("accessibleEmpIds", new ArrayList<>(accessibleEmpIds));
            doc.setMetadata(metadata);

            searchService.indexDocument(doc);

        } catch (Exception e) {
            log.error("Failed to process approval_line CDC event", e);
        }
    }

    // =========================== Helpers ===========================

    private JsonNode extractPayload(String message) throws Exception {
        JsonNode root = objectMapper.readTree(message);
        // Kafka Connect JsonConverter가 schemas.enable=false이면 payload가 최상위,
        // schemas.enable=true면 root.payload 하위. 둘 다 처리.
        JsonNode payload = root.has("payload") ? root.path("payload") : root;
        if (payload.isMissingNode() || payload.isNull()) {
            log.warn("CDC message has no payload: {}", message);
            return null;
        }
        return payload;
    }

    private Object asNumber(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        if (node.isIntegralNumber()) return node.asLong();
        return null;
    }
}
