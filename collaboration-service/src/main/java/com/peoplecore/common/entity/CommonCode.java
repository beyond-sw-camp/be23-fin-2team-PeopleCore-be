package com.peoplecore.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_common_code_group_value", columnNames = {"group_id", "code_value"})
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CommonCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long codeId;

    // MSA 원칙: FK 선언 없이 ID만 보관
    @Column(nullable = false)
    private Long groupId;

    @Column(nullable = false, length = 100)
    private String codeValue;

    @Column(nullable = false, length = 255)
    private String codeName;

    /**
     * 셀렉트박스 표시 순서
     * 저장 전 Service 레이어에서 해당 group 내 max(sort_order) + 1 로 계산하여 주입
     */
    @Column(nullable = false)
    private Integer sortOrder;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    // --- 도메인 메서드 ---

    public void updateCode(String codeName, Integer sortOrder, Boolean isActive) {
        this.codeName = codeName;
        this.sortOrder = sortOrder;
        this.isActive = isActive;
    }
}