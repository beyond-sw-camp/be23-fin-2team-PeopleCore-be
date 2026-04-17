package com.peoplecore.filevault.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.filevault.entity.FolderType;
import com.peoplecore.filevault.service.FileFolderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class FileVaultCdcConsumer {

    private final FileFolderService folderService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "peoplecore.peoplecore.department", groupId = "filevault-cdc")
    public void handleDepartment(String message) {
        try {
            JsonNode payload = extractPayload(message);
            String op = payload.path("op").asText();

            if (!"c".equals(op)) return;

            JsonNode row = payload.path("after");
            Long deptId = row.path("dept_id").asLong();
            UUID companyId = CdcEventParser.decodeUuid(row.path("company_id").asText(null));
            String deptName = row.path("dept_name").asText("부서 파일함");

            if (companyId == null) {
                log.warn("[FileVault CDC] 부서 생성 이벤트에 companyId 없음, deptId={}", deptId);
                return;
            }

            folderService.createSystemDefaultFolder(
                companyId, deptName + " 파일함", FolderType.DEPT, deptId, null, 0L);
            log.info("[FileVault CDC] 부서 기본 파일함 생성 완료 deptId={}", deptId);

        } catch (Exception e) {
            log.error("[FileVault CDC] 부서 이벤트 처리 실패: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "peoplecore.peoplecore.employee", groupId = "filevault-cdc")
    public void handleEmployee(String message) {
        try {
            JsonNode payload = extractPayload(message);
            String op = payload.path("op").asText();

            if (!"c".equals(op)) return;

            JsonNode row = payload.path("after");
            Long empId = row.path("emp_id").asLong();
            UUID companyId = CdcEventParser.decodeUuid(row.path("company_id").asText(null));
            String empName = row.path("emp_name").asText("개인");

            if (companyId == null) {
                log.warn("[FileVault CDC] 사원 생성 이벤트에 companyId 없음, empId={}", empId);
                return;
            }

            folderService.createSystemDefaultFolder(
                companyId, empName + "의 파일함", FolderType.PERSONAL, null, empId, empId);
            log.info("[FileVault CDC] 개인 파일함 생성 완료 empId={}", empId);

        } catch (Exception e) {
            log.error("[FileVault CDC] 사원 이벤트 처리 실패: {}", e.getMessage());
        }
    }

    private JsonNode extractPayload(String message) throws Exception {
        JsonNode root = objectMapper.readTree(message);
        return root.has("payload") ? root.path("payload") : root;
    }
}
