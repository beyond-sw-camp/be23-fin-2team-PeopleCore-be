package com.peoplecore.filevault.service;

import com.peoplecore.exception.BusinessException;
import io.minio.*;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class FileVaultMinioService {

    private final MinioClient minioClient;
    private final String bucket;

    public FileVaultMinioService(
        MinioClient minioClient,
        @Value("${minio.filevault.bucket:filevault}") String bucket
    ) {
        this.minioClient = minioClient;
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
            return minioClient.getPresignedObjectUrl(
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
        try {
            return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(storageKey)
                    .expiry(expiryMinutes, TimeUnit.MINUTES)
                    .build());
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
