package com.peoplecore.filevault.service;

import com.peoplecore.exception.BusinessException;
import io.minio.*;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class FileVaultMinioService {

    private final MinioClient minioClient;
    private final MinioClient minioPresignClient;
    private final String bucket;

    public FileVaultMinioService(
        @Qualifier("minioClient") MinioClient minioClient,
        @Qualifier("minioPresignClient") MinioClient minioPresignClient,
        @Value("${minio.filevault.bucket:filevault}") String bucket
    ) {
        this.minioClient = minioClient;
        this.minioPresignClient = minioPresignClient;
        this.bucket = bucket;
    }

    @Bean
    public ApplicationRunner ensureFileVaultBucket() {
        return args -> {
            boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(
                    MakeBucketArgs.builder().bucket(bucket).build());
                log.info("[FileVault] 버킷 '{}' 자동 생성 완료", bucket);
            }
        };
    }

    public String generatePresignedPutUrl(String storageKey, int expiryMinutes) {
        try {
            return minioPresignClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(bucket)
                    .object(storageKey)
                    .expiry(expiryMinutes, TimeUnit.MINUTES)
                    .build());
        } catch (Exception e) {
            log.error("Presigned PUT URL 생성 실패 key={}, error={}", storageKey, e.getMessage());
            throw new BusinessException("파일 업로드 URL 생성에 실패했습니다.", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    public String generatePresignedGetUrl(String storageKey, int expiryMinutes) {
        return generatePresignedGetUrl(storageKey, expiryMinutes, null);
    }

    /**
     * @param attachmentFilename non-null이면 response-content-disposition=attachment; filename*=UTF-8''<percent-encoded>
     *                           쿼리 파라미터를 붙여 MinIO가 다운로드 응답 시 해당 Content-Disposition을 반환하도록 함.
     *                           null이면 기본(inline) 동작.
     */
    public String generatePresignedGetUrl(String storageKey, int expiryMinutes, String attachmentFilename) {
        try {
            GetPresignedObjectUrlArgs.Builder builder = GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(bucket)
                .object(storageKey)
                .expiry(expiryMinutes, TimeUnit.MINUTES);
            if (attachmentFilename != null && !attachmentFilename.isEmpty()) {
                String encoded = URLEncoder.encode(attachmentFilename, StandardCharsets.UTF_8).replace("+", "%20");
                String disposition = "attachment; filename*=UTF-8''" + encoded;
                builder.extraQueryParams(Map.of("response-content-disposition", disposition));
            }
            return minioPresignClient.getPresignedObjectUrl(builder.build());
        } catch (Exception e) {
            log.error("Presigned GET URL 생성 실패 key={}, error={}", storageKey, e.getMessage());
            throw new BusinessException("파일 다운로드 URL 생성에 실패했습니다.", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    public long headObject(String storageKey) {
        try {
            StatObjectResponse stat = minioClient.statObject(
                StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(storageKey)
                    .build());
            return stat.size();
        } catch (Exception e) {
            log.warn("HEAD 실패 (객체 미존재 가능) key={}, error={}", storageKey, e.getMessage());
            return -1;
        }
    }

    public void deleteObject(String storageKey) {
        try {
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(storageKey)
                    .build());
        } catch (Exception e) {
            log.error("MinIO 객체 삭제 실패 key={}, error={}", storageKey, e.getMessage());
            throw new BusinessException("파일 삭제에 실패했습니다.", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}
