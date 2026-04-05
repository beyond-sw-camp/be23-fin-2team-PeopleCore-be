package com.peoplecore.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    uniqueConstraints = {
        // 사원 한 명이 동일 엔티티를 중복 즐겨찾기 방지
        @UniqueConstraint(
            name = "uk_bookmark_entity_emp",
            columnNames = {"entity_type", "entity_id", "emp_id"}
        )
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CommonBookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bookmarkId;

    @Column(nullable = false, columnDefinition = "BINARY(16)")
    private UUID companyId;

    @Column(nullable = false, length = 100)
    private String entityType;

    @Column(nullable = false)
    private Long entityId;

    @Column(nullable = false)
    private Long empId;

    @Column(nullable = false, length = 100)
    private String empName;

    @Column(nullable = false, length = 100)
    private String empDeptName;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}