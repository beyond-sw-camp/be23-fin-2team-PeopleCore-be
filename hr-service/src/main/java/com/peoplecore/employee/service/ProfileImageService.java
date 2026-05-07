package com.peoplecore.employee.service;

import io.minio.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@Service
@Slf4j
@Transactional
public class ProfileImageService {

    private final MinioClient minioClient;
    private static final String PROFILE_BUCKET = "peoplecore-profile";
    private static final long MAX_SIZE = 5L * 1024 * 1024;
    private static final String URL_PREFIX = "/employee/profile-images/";

    @Autowired
    public ProfileImageService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }


    @PostConstruct
    public void init() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(PROFILE_BUCKET).build()
            );
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(PROFILE_BUCKET).build());
                log.info("[MinIO] 프로필 이미지 버킷 생성 완료: {}", PROFILE_BUCKET);
            }
            // 공개 읽기 정책 — 프록시 GET이 기본이지만, 직접 접근도 허용
            String publicPolicy = """
                    {
                      "Version": "2012-10-17",
                      "Statement": [{
                        "Effect": "Allow",
                        "Principal": {"AWS": ["*"]},
                        "Action": ["s3:GetObject"],
                        "Resource": ["arn:aws:s3:::%s/*"]
                      }]
                    }
                    """.formatted(PROFILE_BUCKET);
            minioClient.setBucketPolicy(
                    SetBucketPolicyArgs.builder().bucket(PROFILE_BUCKET).config(publicPolicy).build()
            );
        } catch (Exception e) {
            log.error("[MinIO] 프로필 버킷 초기화 실패: {}", e.getMessage(), e);
        }
        // 프로필 이미지는 lifecycle 미적용 (자동 삭제 X)
    }

    /** 업로드 → 새 URL 반환 */
    public String upload(Long empId, MultipartFile file) throws Exception {
        validate(file);
        String original = file.getOriginalFilename() == null ? "image" : file.getOriginalFilename();
        String objectName = "emp-" + empId + "/" + UUID.randomUUID() + "_" + original;

        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(PROFILE_BUCKET)
                        .object(objectName)
                        .stream(file.getInputStream(), file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build()
        );
        return URL_PREFIX + objectName;
    }

    /** 기존 이미지 삭제 (best-effort, 실패해도 throw X) */
    public void deleteByUrl(String url) {
        if (url == null || !url.startsWith(URL_PREFIX)) return;
        String objectName = url.substring(URL_PREFIX.length());
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder().bucket(PROFILE_BUCKET).object(objectName).build()
            );
        } catch (Exception e) {
            log.warn("[MinIO] 프로필 이미지 삭제 실패 (무시): objectName={}, error={}", objectName, e.getMessage());
        }
    }

    /** 프록시 GET용 스트림 */
    public InputStream download(String objectName) throws Exception {
        return minioClient.getObject(
                GetObjectArgs.builder().bucket(PROFILE_BUCKET).object(objectName).build()
        );
    }

    public StatObjectResponse stat(String objectName) throws Exception {
        return minioClient.statObject(
                StatObjectArgs.builder().bucket(PROFILE_BUCKET).object(objectName).build()
        );
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("파일이 없습니다");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException("이미지 크기는 5MB 이하여야 합니다");
        }
        String ct = file.getContentType();
        if (ct == null || !ct.startsWith("image/")) {
            throw new IllegalArgumentException("이미지 파일만 업로드 가능합니다");
        }
    }
}

