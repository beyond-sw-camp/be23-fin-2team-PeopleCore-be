package com.peoplecore.collaboration_service.common.entity;

import com.peoplecore.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

/**
 * 공통 댓글
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommonComment extends BaseTimeEntity {

    /** 공통 댓글 Id */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long commentId;

    /** 회사 Id */
    @Column(nullable = false)
    private UUID companyId;

    /** 대상 구분 id */
    @Column(nullable = false)
    private Long commentEntityId;

    /** 대상 구분 */
    @Column(nullable = false)
    private String commentEntityType;

    /** 상위 댓글 Id - null일 경우 원본 댓글 */
    private Long commentParentId;

    /** 작성자 Id */
    @Column(nullable = false)
    private Long empId;

    /** 작성자 이름 */
    @Column(nullable = false)
    private String empName;

    /** 작성자 부서 */
    @Column(nullable = false)
    private String empDeptName;

    /** 내용 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String commentContent;

    /** 작성자 직급 */
    @Column(nullable = false)
    private String empGrade;

    /** 작성자 직책 */
    @Column(nullable = false)
    private String empTitle;

}
