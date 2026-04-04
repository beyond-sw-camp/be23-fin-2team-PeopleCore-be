package com.peoplecore.approval.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 결재 문서 상태 변경 이력
 * 상태가 바뀔 때마다 INSERT → 반려/재기안/회수 히스토리 추적
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalStatusHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long historyId;

    /** 결재 문서 ID */
    @Column(nullable = false)
    private Long docId;

    /** 회사 ID */
    @Column(nullable = false)
    private UUID companyId;

    /** 변경 전 상태 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus previousStatus;

    /** 변경 후 상태 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus changedStatus;

    /** 변경자 (기안자 or 결재자) */
    @Column(nullable = false)
    private Long changedBy;

    /** 변경 사유 (반려 사유, 재기안, 회수 등) */
    private String changeReason;

    /** 변경 시각 */
    @Column(nullable = false)
    private LocalDateTime changedAt;
}
