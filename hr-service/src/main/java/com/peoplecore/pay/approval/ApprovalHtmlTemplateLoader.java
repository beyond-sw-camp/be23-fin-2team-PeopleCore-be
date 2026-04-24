package com.peoplecore.pay.approval;

import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 결의서 HTML 템플릿 로더
 * - 회사 생성 시 collaboration-service 의 ApprovalFormService 가
 *   `forms/{companyId}/{formCode}_v1.html` 경로로 MinIO 에 업로드한 템플릿을 읽어옴
 * - 결의서 양식은 PROTECTED 로 수정 불가 → formVersion 은 항상 1 (v1 고정 조회)
 * - 회사별 lazy load + 메모리 캐시 (기동 시 pre-load 안 함)
 */
@Slf4j
@Component
public class ApprovalHtmlTemplateLoader {

    private final MinioClient minioClient;
    private static final String bucketName = "approval-form";

    // key = companyId + ":" + formCode (회사별로 템플릿 분리 캐싱)
    private final ConcurrentMap<String, String> cache = new ConcurrentHashMap<>();

    @Autowired
    public ApprovalHtmlTemplateLoader(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    public String load(UUID companyId, ApprovalFormType type) {
        String key = companyId + ":" + type.getFormCode();
        return cache.computeIfAbsent(key, k -> fetchFromMinio(companyId, type));
    }

    private String fetchFromMinio(UUID companyId, ApprovalFormType type) {
        String objectName = String.format("forms/%s/%s_v1.html", companyId, type.getFormCode());
        try (InputStream is = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build())) {
            String html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            log.info("[ApprovalTemplate] 로딩 - companyId={}, type={}, size={}", companyId, type, html.length());
            return html;
        } catch (Exception e) {
            // 회사 생성 플로우가 안 돌았거나 MinIO 에 파일이 없는 상태
            log.error("[ApprovalTemplate] 로딩 실패 - companyId={}, type={}, object={}", companyId, type, objectName, e);
            throw new CustomException(ErrorCode.APPROVAL_TEMPLATE_NOT_FOUND);
        }
    }
}
