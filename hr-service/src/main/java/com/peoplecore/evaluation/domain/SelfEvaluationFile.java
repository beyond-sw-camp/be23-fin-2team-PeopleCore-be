package com.peoplecore.evaluation.domain;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

// 자기평가 근거 파일 - SelfEvaluation 1 : N. MinIO 업로드
@Entity
@Table(name = "self_evaluation_file")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SelfEvaluationFile extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "file_id")
    private Long fileId; // 파일 PK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "self_eval_id", nullable = false)
    private SelfEvaluation selfEvaluation; // 소속 자기평가

    @Column(name = "original_file_name", nullable = false)
    private String originalFileName; // 원본 파일명

    @Column(name = "stored_file_path", nullable = false)
    private String storedFilePath; // MinIO 저장 경로 (MinioService.uploadFile 반환값)

    @Column(name = "content_type")
    private String contentType; // MIME 타입

    @Column(name = "file_size")
    private Long fileSize; // 파일 크기 (bytes)
}
