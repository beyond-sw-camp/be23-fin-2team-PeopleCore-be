package com.peoplecore.approval.entity;

import com.peoplecore.entity.BaseTimeEntity;
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

    /** 문서함 이름 */
    @Column(nullable = false, length = 100)
    private String folderName;

    /** 정렬 순서 */
    @Column(nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

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
    private Boolean receivedYn = true;

    /** 발신함 사용 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean sentYn = true;

    /** 참조함 사용 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean ccYn = true;

    /** 열람함 사용 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean viewYn = true;

    /** 이름 수정 */
    public void updateName(String folderName) {
        this.folderName = folderName;
    }

    /** 순서 변경 */
    public void updateSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
