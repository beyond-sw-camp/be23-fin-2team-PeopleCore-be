package com.peoplecore.collaboration_service.board.entity;

import com.peoplecore.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

/**
 * 게시판 접근 권한
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoardAccess extends BaseTimeEntity {

    /** 접근 권한 id */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long accessId;

    /** 게시판  카테고리 ID */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private BoardCategory boardCategoryId;

    /** 회사 ID */
    @Column(nullable = false)
    private UUID companyId;

    /** 대상 구분 - dept==부서 , emp==개인 */
    @Column(nullable = false)
    private String accessTargetType;

    /** 대상 번호 - 부서 id 또는 사원 id */
    @Column(nullable = false)
    private Long accessTargetId;

    /** 읽기 권한 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean accessCanRead = false;

    /** 쓰기 권한 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean accessCanWrite =  false;

    /** 수정 권한 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean accessCanEdit = false;

    /** 삭제 권한 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean accessCanDelete = false;

    /** 공지 고정 권한 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean accessCanPin = false;

    /** 댓글 권한 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean accessCanComment = false;

    /** 관리 권한 - 타인 글 삭제 권한 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean accessCanManage = false;

}
