package com.peoplecore.common.service;

import com.peoplecore.exception.BusinessException;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class MinioService {
    private final MinioClient minioClient;
    private final MinioClient minioPresignClient;
    private final String bucket;
    private final String endpoint;

    @Autowired
    public MinioService(
            @Qualifier("minioClient") MinioClient minioClient,
            @Qualifier("minioPresignClient") MinioClient minioPresignClient,
            @Value("${minio.bucket}") String bucket,
            @Value("${minio.endpoint}") String endpoint
    ) {
        this.minioClient = minioClient;
        this.minioPresignClient = minioPresignClient;
        this.bucket = bucket;
        this.endpoint = endpoint;
    }

    @Value("${minio.public-url}")
    private String publicUrl;

    /**
     * 공개 버킷 직접 접근 URL 생성
     */
    public String getPublicUrl(String objectName) {
        return publicUrl + "/" + bucket + "/" + objectName;
    }



    /* minio에서 양식 다운로드 */
    public String getFormHtml(String objectName) {
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .build())) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("MinIO 양식 HTML 조회 실패 objectName={}, error={}", objectName, e.getMessage());
            throw new BusinessException("양식 파일을 불러올 수 없습니다.", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    /*minio에 양식 업로드 */
    public void uploadFormHtml(String objectName, String htmlContext) {
        try {
            byte[] bytes = htmlContext.getBytes(StandardCharsets.UTF_8);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                            .contentType("text/html")
                            .build());

        } catch (Exception e) {
            log.error("Minio 양식 html 업로드 실패 objectName = {} , error = {} ", objectName, e.getMessage());
            throw new BusinessException("양식 파일 업로드 실패", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    /**
     * 첨부파일 업로드 (MultipartFile → MinIO)
     */
    public void uploadAttachment(String objectName, MultipartFile file) {
        try (InputStream stream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .stream(stream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build());
            log.info("[MinIO putObject 성공] bucket={}, objectName={}, contentType={}",
                    bucket, objectName, file.getContentType());
        } catch (Exception e) {
            log.error("MinIO 첨부파일 업로드 실패 objectName={}, error={}", objectName, e.getMessage());
            throw new BusinessException("첨부파일 업로드에 실패했습니다.", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    /**
     * 첨부파일 다운로드용 Pre-signed URL 생성 (유효시간 1시간)
     */
    public String getPresignedUrl(String objectName) {
        try {
            return minioPresignClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectName)
                            .expiry(1, TimeUnit.HOURS)
                            .build());
        } catch (Exception e) {
            log.error("MinIO Pre-signed URL 생성 실패 objectName={}, error={}", objectName, e.getMessage());
            throw new BusinessException("첨부파일 URL 생성에 실패했습니다.", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    /**
     * 첨부파일 삭제
     */
    public void deleteObject(String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build());
        } catch (Exception e) {
            log.error("MinIO 파일 삭제 실패 objectName={}, error={}", objectName, e.getMessage());
            throw new BusinessException("파일 삭제에 실패했습니다.", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}
