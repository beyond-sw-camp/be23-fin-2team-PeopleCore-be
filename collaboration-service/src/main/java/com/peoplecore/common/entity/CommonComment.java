package com.peoplecore.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CommonComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long commentId;

    /**
     * null이면 최상위 댓글, 값이 있으면 대댓글
     * 자기참조이지만 MSA 단순성을 위해 FK 선언 없이 ID만 보관
     */
    private Long parentCommentId;

    @Column(nullable = false, columnDefinition = "BINARY(16)")
    private UUID companyId;

    @Column(nullable = false, length = 100)
    private String entityType;

    @Column(nullable = false)
    private Long entityId;

    @Column(nullable = false)
    private Long empId;

    // 반정규화 스냅샷
    @Column(nullable = false, length = 100)
    private String empName;

    @Column(nullable = false, length = 100)
    private String empDeptName;

    @Column(nullable = false, length = 100)
    private String empGradeName;

    @Column(nullable = false, length = 100)
    private String empTitleName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    // --- 도메인 메서드 ---

    public void updateContent(String content) {
        this.content = content;
    }
}