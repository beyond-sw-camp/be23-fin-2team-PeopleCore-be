package com.peoplecore.approval.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

/**
 * 부서 문서함 담당자
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeptFolderManager extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long managerId;

    /** 부서 문서함 ID */
    @Column(nullable = false)
    private Long deptAppFolderId;

    /** 회사 ID */
    @Column(nullable = false)
    private UUID companyId;

    /** 담당자 사원 ID */
    @Column(nullable = false)
    private Long empId;

    /** 담당자 이름 (조회 편의) */
    @Column(nullable = false, length = 50)
    private String empName;

    /** 담당자 부서명 (조회 편의) */
    @Column(nullable = false, length = 100)
    private String deptName;
}
