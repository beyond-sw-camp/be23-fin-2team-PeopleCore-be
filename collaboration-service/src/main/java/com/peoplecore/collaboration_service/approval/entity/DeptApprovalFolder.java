package com.peoplecore.collaboration_service.approval.entity;

import com.peoplecore.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

/**
 * 부서 문서함 설정
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeptApprovalFolder extends BaseTimeEntity {

    /** 부서 문서함 설정 id */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long deptAppFolderId;

    /** 부서 id */
    @Column(nullable = false)
    private Long deptId;

    /** 회사 id */
    @Column(nullable = false)
    private UUID companyId;

    /** 전체 사용 여부 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /** 생성자 id */
    @Column(nullable = false)
    private Long empId;

    /** 대기함 사용 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean pendingYn = true;

    /** 수신함 사용 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean ReceivedYn = true;

    /** 발신함 사용 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean SentYn = true;

    /** 참조함 사용 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean ccYn = true;

    /** 열람함 사용 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean viewYn = true;

}
