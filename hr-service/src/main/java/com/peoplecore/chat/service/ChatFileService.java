package com.peoplecore.chat.service;

import io.minio.*;
import io.minio.messages.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatFileService {

    private final MinioClient minioClient;
    private static final String CHAT_BUCKET = "peoplecore-chat";

    @Value("${minio.endpoint}")
    private String minioEndpoint;

    @PostConstruct
    public void init() {
        // 1. 버킷 생성
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(CHAT_BUCKET).build()
            );
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(CHAT_BUCKET).build()
                );
                log.info("[MinIO] 채팅 버킷 생성 완료: {}", CHAT_BUCKET);
            }
        } catch (Exception e) {
            log.error("[MinIO] 채팅 버킷 생성 실패: {}", e.getMessage(), e);
            return;
        }

        // 2. 공개 읽기 정책 (브라우저에서 이미지 URL 접근 가능)
        try {
            String publicPolicy = """
                    {
                      "Version": "2012-10-17",
                      "Statement": [
                        {
                          "Effect": "Allow",
                          "Principal": {"AWS": ["*"]},
                          "Action": ["s3:GetObject"],
                          "Resource": ["arn:aws:s3:::%s/*"]
                        }
                      ]
                    }
                    """.formatted(CHAT_BUCKET);

            minioClient.setBucketPolicy(
                    SetBucketPolicyArgs.builder()
                            .bucket(CHAT_BUCKET)
                            .config(publicPolicy)
                            .build()
            );
            log.info("[MinIO] 채팅 버킷 공개 읽기 정책 설정 완료");
        } catch (Exception e) {
            log.error("[MinIO] 버킷 정책 설정 실패: {}", e.getMessage());
        }

        // 3. 30일 자동 삭제 Lifecycle
        try {
            LifecycleRule rule = new LifecycleRule(
                    Status.ENABLED,
                    null,                                           // abortIncompleteMultipartUpload
                    new Expiration((java.time.ZonedDateTime) null, 30, null),  // days=30
                    new RuleFilter(""),                             // prefix="" (전체 적용)
                    "chat-file-expiry",                             // rule id
                    null,                                           // noncurrentVersionExpiration
                    null,                                           // noncurrentVersionTransition
                    null                                            // transition
            );

            minioClient.setBucketLifecycle(
                    SetBucketLifecycleArgs.builder()
                            .bucket(CHAT_BUCKET)
                            .config(new LifecycleConfiguration(List.of(rule)))
                            .build()
            );
            log.info("[MinIO] 채팅 버킷 Lifecycle 설정 완료 (30일 자동 삭제)");
        } catch (Exception e) {
            log.warn("[MinIO] Lifecycle 설정 실패 (파일은 정상 업로드 가능): {}", e.getMessage());
        }
    }

    public ChatFileResult uploadFile(MultipartFile file, Long roomId) throws Exception {
        String originalName = file.getOriginalFilename();
        String storedName = "room-" + roomId + "/" + UUID.randomUUID() + "_" + originalName;

        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(CHAT_BUCKET)
                        .object(storedName)
                        .stream(file.getInputStream(), file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build()
        );

        // MinIO 직접 URL 대신 백엔드 프록시 경로 반환
        String fileUrl = "/chat/files/" + storedName;

        return new ChatFileResult(
                fileUrl,
                originalName,
                file.getSize(),
                isImageContentType(file.getContentType())
        );
    }

    public java.io.InputStream downloadFile(String objectName) throws Exception {
        return minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(CHAT_BUCKET)
                        .object(objectName)
                        .build()
        );
    }

    public StatObjectResponse getFileStat(String objectName) throws Exception {
        return minioClient.statObject(
                StatObjectArgs.builder()
                        .bucket(CHAT_BUCKET)
                        .object(objectName)
                        .build()
        );
    }

    private boolean isImageContentType(String contentType) {
        return contentType != null && contentType.startsWith("image/");
    }

    public record ChatFileResult(
            String fileUrl,
            String fileName,
            long fileSize,
            boolean isImage
    ) {}
}
