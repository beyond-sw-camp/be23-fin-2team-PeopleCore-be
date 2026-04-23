package com.peoplecore.pay.approval;

import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

/**
 * 결의서 HTML 템플릿 로더
 * - resources/approval-templates/{파일명} 한 번만 읽어 메모리 캐시
 */
@Slf4j
@Component
public class ApprovalHtmlTemplateLoader {

    private final MinioClient minioClient;
    private static final String bucketName = "approval-form";

    private final Map<ApprovalFormType, String> cache = new EnumMap<>(ApprovalFormType.class);

    public ApprovalHtmlTemplateLoader(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @PostConstruct
    public void init(){
        for(ApprovalFormType type : ApprovalFormType.values()){
            try (
                InputStream is = minioClient.getObject(
                        GetObjectArgs.builder()
                                .bucket(bucketName)
                                .object(type.getTemplateFileName())
                                .build()
                    )
            ){ String html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                cache.put(type, html);
                log.info("[ApprovalTemplate] 로딩 완료 - type={}, size={}", type, html.length());
            } catch (Exception e) {
                throw new IllegalStateException("결의서 템플릿 로딩 실패" + type, e);
            }
        }

    }
    public String load(ApprovalFormType type){
        String html = cache.get(type);
        if (html == null){
            throw new CustomException(ErrorCode.APPROVAL_TEMPLATE_NOT_FOUND);
        }
        return html;
    }


}
