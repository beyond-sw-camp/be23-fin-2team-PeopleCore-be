package com.peoplecore.minio.service;

import com.peoplecore.minio.config.MinioConfig;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@Service
@Slf4j
public class MinioService {
    private final MinioClient minioClient;

    public MinioService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @Value("${minio.bucket-name}")
    private String bucketName;

    @PostConstruct
    public void init() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build()
            );
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("[MinIO] 버킷 생성 완료: {}", bucketName);
            }
        } catch (Exception e) {
            log.error("[MinIO] 버킷 초기화 실패 bucket={}, error={}", bucketName, e.getMessage(), e);
        }
    }

//  파일 업로드
    public String uploadFile(MultipartFile file, String folder) throws Exception {
        // 고유 파일명 생성
        String fileName = folder + "/" + UUID.randomUUID() + "_" + file.getOriginalFilename();
        // try-with-resources 로 InputStream 자동 close → Direct ByteBuffer 누수 방지
        try (InputStream stream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(fileName)
                            .stream(stream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
        }
        return fileName;
    }
//        파일삭제
    public void  deleteFile(String fileName) throws Exception {
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(fileName)
                        .build()
        );
    }

//    파일 다운로드
    public InputStream downloadFile(String fileName) throws Exception {
        return minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(fileName)
                        .build()
        );


    }



}
